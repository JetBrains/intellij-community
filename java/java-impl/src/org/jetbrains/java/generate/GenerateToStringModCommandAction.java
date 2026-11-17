// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.GenerateToStringClassFilter;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.config.InsertLastStrategy;
import org.jetbrains.java.generate.exception.GenerateCodeException;
import org.jetbrains.java.generate.psi.PsiAdapter;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a {@code toString()} method with a {@link ModEditOptions} form instead of a Swing dialog.
 * Thus, the action works in each client that shows a {@link ModEditOptions} form, for example, in LSP.
 * <p>
 * The form has a member selector, a template drop-down, and the "Insert @Override" checkbox.
 * Unlike {@link GenerateToStringAction}, the form has no button for the template settings.
 * The drop-down adds the "(incompatible)" suffix to a template that the class cannot use.
 */
public final class GenerateToStringModCommandAction implements ModCommandAction {
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("generate.tostring.title");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return findTargetClass(context) == null ? null : Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiClass psiClass = findTargetClass(context);
    if (psiClass == null) return ModCommand.nop();

    List<PsiElementClassMember<?>> members = List.of(GenerateToStringActionHandlerImpl.buildMembersToShow(psiClass));
    List<PsiElementClassMember<?>> preselection = ContainerUtil.filter(members, m -> m.getElement().getContainingClass() == psiClass);

    TemplatesManager templatesManager = ToStringTemplatesManager.getInstance();
    List<OptDropdown.Option> templateOptions = createTemplateOptions(psiClass, templatesManager.getAllTemplates());
    String defaultTemplate = templatesManager.getDefaultTemplate().getFileName();

    boolean annotationsAvailable = PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, psiClass);
    boolean insertOverride = annotationsAvailable &&
                             JavaCodeStyleSettings.getInstance(psiClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION;

    return new ModEditOptions<>(
      getFamilyName(),
      () -> new ToStringOptions(members, templateOptions, annotationsAvailable, preselection, defaultTemplate, insertOverride),
      true,
      options -> generate(context, psiClass, members, options));
  }

  private static @Nullable PsiClass findTargetClass(@NotNull ActionContext context) {
    if (!(context.file() instanceof PsiJavaFile javaFile)) return null;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiClass.class, false);
    if (psiClass == null) {
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length != 1 || !(classes[0] instanceof PsiImplicitClass implicitClass) ||
          implicitClass.getFirstChild() == null || !PsiMethodUtil.hasMainMethod(implicitClass)) {
        return null;
      }
      psiClass = implicitClass;
    }
    for (GenerateToStringClassFilter filter : GenerateToStringClassFilter.EP_NAME.getExtensionList()) {
      if (!filter.canGenerateToString(psiClass)) return null;
    }
    return psiClass;
  }

  /**
   * Creates a drop-down option for each template.
   * The label of a template has the "(incompatible)" suffix when the resolve scope of the class has no class that the template needs.
   */
  private static @NotNull List<OptDropdown.Option> createTemplateOptions(@NotNull PsiClass psiClass,
                                                                         @NotNull Collection<TemplateResource> templates) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(psiClass.getProject());
    GlobalSearchScope resolveScope = psiClass.getResolveScope();
    return ContainerUtil.map(templates, template -> {
      String name = template.getName();
      String className = template.getClassName();
      boolean incompatible = className != null && facade.findClass(className, resolveScope) == null;
      return OptPane.option(name, incompatible ? JavaBundle.message("generate.tostring.template.incompatible", name) : name);
    });
  }

  private static @NotNull ModCommand generate(@NotNull ActionContext context,
                                              @NotNull PsiClass psiClass,
                                              @NotNull List<PsiElementClassMember<?>> allMembers,
                                              @NotNull ToStringOptions options) {
    TemplateResource template = ToStringTemplatesManager.getInstance().findTemplateByName(options.template);
    if (template == null || !template.isValidTemplate()) {
      return ModCommand.error(JavaBundle.message("hint.text.tostring.template.invalid", options.template));
    }
    Config config = GenerateToStringContext.getConfig();
    List<? extends PsiElementClassMember<?>> selected =
      config.getSortElements() == 0 ? options.members : ContainerUtil.filter(allMembers, options.members::contains);
    List<PsiMember> members = GenerationUtil.convertClassMembersToPsiMembers(selected);

    GeneratedToString generated;
    try {
      generated = GeneratedToString.create(psiClass, members, template, config);
    }
    catch (GenerateCodeException e) {
      return ModCommand.error(JavaBundle.message("generate.tostring.handle.exception.velocity.error.message", e.getMessage()));
    }
    if (generated == null) {
      return ModCommand.error(JavaBundle.message("hint.text.tostring.method.could.not.be.created.from.template", template.getFileName()));
    }

    int offset = context.offset();
    boolean insertOverride = options.insertOverride;
    PsiMethod existingMethod = generated.existingMethod();
    if (existingMethod == null) {
      return ModCommand.psiUpdate(psiClass, (cls, updater) -> insert(cls, null, generated, offset, insertOverride, updater));
    }
    return switch (config.getReplaceDialogInitialOption()) {
      case DUPLICATE -> ModCommand.psiUpdate(existingMethod, (method, updater) ->
        insert(method.getContainingClass(), null, generated, offset, insertOverride, updater));
      case REPLACE -> ModCommand.psiUpdate(existingMethod, (method, updater) ->
        insert(method.getContainingClass(), method, generated, offset, insertOverride, updater));
      case ASK -> ModCommand.chooseAction(
        JavaBundle.message("generate.tostring.method.already.exists.dialog.title"),
        ModCommand.psiUpdateStep(
          existingMethod,
          JavaBundle.message("generate.tostring.method.already.exists.dialog.message", existingMethod.getName()),
          (method, updater) -> insert(method.getContainingClass(), method, generated, offset, insertOverride, updater)),
        ModCommand.psiUpdateStep(
          existingMethod,
          JavaBundle.message("generate.tostring.method.duplicate", existingMethod.getName()),
          (method, updater) -> insert(method.getContainingClass(), null, generated, offset, insertOverride, updater)));
    };
  }

  /**
   * Inserts the generated method into the writable copy of the class.
   *
   * @param existingMethod the method to delete after the insertion, or null to keep all the methods
   */
  private static void insert(@Nullable PsiClass psiClass,
                             @Nullable PsiMethod existingMethod,
                             @NotNull GeneratedToString generated,
                             int offset,
                             boolean insertOverride,
                             @NotNull ModPsiUpdater updater) {
    if (psiClass == null) return;
    Project project = psiClass.getProject();
    JVMElementFactory factory = JVMElementFactories.getFactory(psiClass.getLanguage(), project);
    if (factory == null) return;
    PsiMethod newMethod = factory.createMethodFromText(generated.methodText(), psiClass);
    CodeStyleManager.getInstance(project).reformat(newMethod);

    Config config = GenerateToStringContext.getConfig();
    PsiMethod method = switch (config.getInsertNewMethodInitialOption()) {
      case AT_CARET -> insertAtOffset(psiClass, newMethod, offset);
      case AFTER_EQUALS_AND_HASHCODE -> insertAfterEqualsAndHashCode(psiClass, newMethod, offset);
      case AT_THE_END_OF_A_CLASS -> InsertLastStrategy.getInstance().insertNewMethod(psiClass, newMethod, null);
    };
    if (method == null) return;
    if (existingMethod != null) {
      existingMethod.delete();
    }
    if (insertOverride) {
      method.getModifierList().addAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
    }
    if (generated.existingJavaDoc() != null || generated.javaDoc() != null) {
      GenerationUtil.applyJavaDoc(method, generated.existingJavaDoc(), generated.javaDoc());
    }
    if (generated.autoImportPackages() != null && psiClass.getContainingFile() instanceof PsiJavaFile javaFile) {
      // Old user templates can ask for the import of packages.
      for (String packageName : generated.autoImportPackages().split(",")) {
        PsiAdapter.addImportStatement(javaFile, packageName.trim());
      }
    }
    method = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(method);
    if (config.isJumpToMethod()) {
      updater.moveCaretTo(method.getTextOffset());
    }
  }

  private static @NotNull PsiMethod insertAtOffset(@NotNull PsiClass psiClass, @NotNull PsiMethod newMethod, int offset) {
    PsiGenerationInfo<PsiMethod> info = new PsiGenerationInfo<>(newMethod, false);
    GenerateMembersUtil.insertMembersAtOffset(psiClass, offset, List.of(info));
    return info.getPsiMember();
  }

  private static @NotNull PsiMethod insertAfterEqualsAndHashCode(@NotNull PsiClass psiClass, @NotNull PsiMethod newMethod, int offset) {
    PsiMethod equals = PsiAdapter.findEqualsMethod(psiClass);
    PsiMethod hashCode = PsiAdapter.findHashCodeMethod(psiClass);
    PsiMethod anchor = equals == null ? hashCode :
                       hashCode == null ? equals :
                       equals.getTextOffset() > hashCode.getTextOffset() ? equals : hashCode;
    return anchor == null ? insertAtOffset(psiClass, newMethod, offset) : (PsiMethod)psiClass.addAfter(newMethod, anchor);
  }

  /**
   * The state of the form. The default option controller binds a control to the field with the name of its bind ID.
   * <p>
   * The constructor gets the data of the form from a read action.
   * Thus, {@link #getOptionsPane()} needs no PSI access, and the IDE can call it on the EDT.
   */
  private static final class ToStringOptions implements OptionContainer {
    private final @NotNull List<PsiElementClassMember<?>> myAllMembers;
    private final @NotNull List<OptDropdown.Option> myTemplateOptions;
    private final boolean myAnnotationsAvailable;

    @SuppressWarnings("FieldMayBeFinal") private @NotNull List<? extends PsiElementClassMember<?>> members;
    @SuppressWarnings("FieldMayBeFinal") private @NotNull String template;
    @SuppressWarnings("FieldMayBeFinal") private boolean insertOverride;

    private ToStringOptions(@NotNull List<PsiElementClassMember<?>> allMembers,
                            @NotNull List<OptDropdown.Option> templateOptions,
                            boolean annotationsAvailable,
                            @NotNull List<PsiElementClassMember<?>> members,
                            @NotNull String template,
                            boolean insertOverride) {
      myAllMembers = allMembers;
      myTemplateOptions = templateOptions;
      myAnnotationsAvailable = annotationsAvailable;
      this.members = new ArrayList<>(members);
      this.template = template;
      this.insertOverride = insertOverride;
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      List<OptRegularComponent> components = new ArrayList<>();
      if (!myAllMembers.isEmpty()) {
        components.add(OptPane.multiSelector("members", myAllMembers, OptMultiSelector.SelectionMode.MULTIPLE_OR_EMPTY));
      }
      components.add(OptPane.dropdown("template", JavaBundle.message("generate.tostring.template.label"),
                                      myTemplateOptions.toArray(OptDropdown.Option[]::new)));
      if (myAnnotationsAvailable) {
        components.add(OptPane.checkbox("insertOverride", JavaBundle.message("insert.override.annotation")));
      }
      return new OptPane(components);
    }
  }

  /**
   * The text of the generated method and of its Javadoc.
   *
   * @param existingMethod     the method of the class with the same signature, or null
   * @param existingJavaDoc    the Javadoc of the existing method, or null
   * @param javaDoc            the Javadoc that the template generates, or null
   * @param autoImportPackages the packages to import, separated by commas, or null
   */
  private record GeneratedToString(@NotNull String methodText,
                                   @Nullable PsiMethod existingMethod,
                                   @Nullable String existingJavaDoc,
                                   @Nullable String javaDoc,
                                   @Nullable String autoImportPackages) {
    /**
     * @return the generated text, or null when the template does not produce a valid method
     * @throws GenerateCodeException when Velocity cannot evaluate the template
     */
    static @Nullable GeneratedToString create(@NotNull PsiClass psiClass,
                                              @NotNull Collection<PsiMember> members,
                                              @NotNull TemplateResource template,
                                              @NotNull Config config) {
      JVMElementFactory factory = JVMElementFactories.getFactory(psiClass.getLanguage(), psiClass.getProject());
      if (factory == null) return null;

      PsiMethod prototype = createMethod(factory, evaluate(psiClass, List.of(), Map.of(), template, config), psiClass);
      if (prototype == null) return null;
      PsiMethod existingMethod = psiClass.findMethodBySignature(prototype, false);
      PsiDocComment existingDoc = existingMethod == null ? null : existingMethod.getDocComment();
      String existingJavaDoc = existingDoc == null ? null : existingDoc.getText();

      Map<String, String> params = new HashMap<>();
      if (existingJavaDoc != null) {
        params.put("existingJavaDoc", existingJavaDoc);
      }
      String methodText = evaluate(psiClass, members, params, template, config);
      if (methodText == null || createMethod(factory, methodText, psiClass) == null) return null;

      String javaDoc = template.getJavaDoc();
      if (existingJavaDoc != null || javaDoc != null) {
        javaDoc = GenerationUtil.velocityGenerateCode(psiClass, members, params, javaDoc, config.getSortElements(),
                                                      config.isUseFullyQualifiedName());
      }
      return new GeneratedToString(methodText, existingMethod, existingJavaDoc, javaDoc, params.get("autoImportPackages"));
    }

    private static @Nullable String evaluate(@NotNull PsiClass psiClass,
                                             @NotNull Collection<PsiMember> members,
                                             @NotNull Map<String, String> params,
                                             @NotNull TemplateResource template,
                                             @NotNull Config config) {
      return GenerationUtil.velocityGenerateCode(psiClass, members, List.of(), params, Map.of(), template.getTemplate(),
                                                 config.getSortElements(), config.isUseFullyQualifiedName(), config.isEnableMethods());
    }

    private static @Nullable PsiMethod createMethod(@NotNull JVMElementFactory factory, @Nullable String text, @NotNull PsiClass context) {
      if (text == null) return null;
      try {
        return factory.createMethodFromText(text, context);
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }
  }
}
