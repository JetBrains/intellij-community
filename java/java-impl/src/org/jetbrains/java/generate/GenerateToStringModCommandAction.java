// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.options.JavaConfigurationDialogInfo;
import com.intellij.codeInsight.options.JavaConfigurationDialogKind;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
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
import java.util.Set;

/**
 * Generates a {@code toString()} method with a {@link ModEditOptions} form instead of a Swing dialog.
 * Thus, the action works in each client that shows a {@link ModEditOptions} form, for example, in LSP.
 * <p>
 * The form has a member selector, a template drop-down, the button for the template settings, and the "Insert @Override" checkbox.
 * The drop-down adds the "(incompatible)" suffix to a template that the class cannot use.
 * The drop-down initially selects the default template, or the first compatible template if the class cannot use the default template.
 * The action makes the selected template the default one.
 */
public final class GenerateToStringModCommandAction implements ModCommandAction {
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("generate.tostring.title");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return GenerateToStringClassFilter.findTargetClass(context) == null ? null : Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiClass psiClass = GenerateToStringClassFilter.findTargetClass(context);
    if (psiClass == null) return ModCommand.nop();

    TemplatesManager templatesManager = ToStringTemplatesManager.getInstance();
    Set<String> unavailableClasses = findUnavailableClasses(psiClass, templatesManager.getAllTemplates());
    String defaultTemplate = chooseTemplate(templatesManager, unavailableClasses);

    boolean annotationsAvailable = PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, psiClass);
    boolean insertOverride = annotationsAvailable &&
                             JavaCodeStyleSettings.getInstance(psiClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION;

    return ModCommand.editOptions(
      psiClass,
      getFamilyName(),
      () -> new ToStringOptions(psiClass, unavailableClasses, annotationsAvailable, defaultTemplate, insertOverride),
      true,
      (cls, options) -> templatesManager.updateDefaultTemplate(cls, options.template).andThen(generate(context, cls, options)));
  }

  /**
   * @return the file name of the default template if the class can use it.
   * Otherwise, the file name of the first template that the class can use.
   * If the class can use no template, the file name of the default template.
   */
  private static @NotNull String chooseTemplate(@NotNull TemplatesManager templatesManager, @NotNull Set<String> unavailableClasses) {
    TemplateResource defaultTemplate = templatesManager.getDefaultTemplate();
    if (!unavailableClasses.contains(defaultTemplate.getClassName())) return defaultTemplate.getFileName();
    TemplateResource compatible =
      ContainerUtil.find(templatesManager.getAllTemplates(), template -> !unavailableClasses.contains(template.getClassName()));
    return (compatible == null ? defaultTemplate : compatible).getFileName();
  }

  /**
   * Finds the classes that the templates need and that the resolve scope of the class does not have.
   * Only a bundled template needs a class.
   * Thus, a template that the user adds in the template settings does not change the result.
   */
  private static @NotNull Set<String> findUnavailableClasses(@NotNull PsiClass psiClass, @NotNull Collection<TemplateResource> templates) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(psiClass.getProject());
    GlobalSearchScope resolveScope = psiClass.getResolveScope();
    return StreamEx.of(templates)
      .map(TemplateResource::getClassName)
      .nonNull()
      .filter(className -> facade.findClass(className, resolveScope) == null)
      .toSet();
  }

  /**
   * Creates a drop-down option for each template.
   * The label of a template has the "(incompatible)" suffix when the template needs a class from {@code unavailableClasses}.
   */
  private static @NotNull List<OptDropdown.Option> createTemplateOptions(@NotNull Collection<TemplateResource> templates,
                                                                         @NotNull Set<String> unavailableClasses) {
    return ContainerUtil.map(templates, template -> {
      String name = template.getName();
      boolean incompatible = unavailableClasses.contains(template.getClassName());
      return OptPane.option(name, incompatible ? JavaBundle.message("generate.tostring.template.incompatible", name) : name);
    });
  }

  private static @NotNull ModCommand generate(@NotNull ActionContext context,
                                              @NotNull PsiClass psiClass,
                                              @NotNull ToStringOptions options) {
    TemplateResource template = ToStringTemplatesManager.getInstance().findTemplateByName(options.template);
    if (template == null || !template.isValidTemplate()) {
      return ModCommand.error(JavaBundle.message("hint.text.tostring.template.invalid", options.template));
    }
    Config config = GenerateToStringContext.getConfig();
    List<? extends PsiElementClassMember<?>> selected =
      config.getSortElements() == 0 ? options.members : ContainerUtil.filter(options.myAllMembers, options.members::contains);
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

    int offset = findInsertionOffset(context, psiClass);
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
   * Finds the offset for the "at caret" placement.
   * When the context element is in a field of the class, the offset is the offset of the class name.
   * Thus, the method goes to the end of the class, as for an action that starts from the class name.
   * For example, the context element of the fix of {@code FieldNotUsedInToStringInspection} is the name of the field.
   */
  private static int findInsertionOffset(@NotNull ActionContext context, @NotNull PsiClass psiClass) {
    PsiField field = PsiTreeUtil.getParentOfType(context.element(), PsiField.class, false, PsiClass.class);
    if (field == null || field.getContainingClass() != psiClass) return context.offset();
    PsiElement nameIdentifier = psiClass.getNameIdentifier();
    return nameIdentifier != null ? nameIdentifier.getTextOffset() : psiClass.getTextOffset();
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
   * {@link #getOptionsPane()} reads the current templates and builds the current member list.
   * Thus, the form shows the changes of the template settings after the renderer refreshes the pane.
   * For example, the member list shows the changes of the filter and of the "Enable getters in code generation" option.
   * The IDE can call {@link #getOptionsPane()} on the EDT, so it builds the member list in a read action.
   */
  private static final class ToStringOptions implements OptionContainer {
    private final @NotNull PsiClass myClass;
    private final @NotNull Set<String> myUnavailableClasses;
    private final boolean myAnnotationsAvailable;
    private final @NotNull Language myLanguage;
    private @NotNull List<PsiElementClassMember<?>> myAllMembers = List.of();

    private @NotNull List<? extends PsiElementClassMember<?>> members = List.of();
    private @NotNull String template;
    @SuppressWarnings("FieldMayBeFinal") private boolean insertOverride;

    private ToStringOptions(@NotNull PsiClass psiClass,
                            @NotNull Set<String> unavailableClasses,
                            boolean annotationsAvailable,
                            @NotNull String template,
                            boolean insertOverride) {
      myClass = psiClass;
      myUnavailableClasses = unavailableClasses;
      myAnnotationsAvailable = annotationsAvailable;
      myLanguage = psiClass.getLanguage();
      this.template = template;
      this.insertOverride = insertOverride;
      updateMembers();
    }

    /**
     * Builds the member list again.
     * The selection keeps the selected members that the new list has.
     * The selection also gets the new members of the class itself, as the initial selection does.
     */
    private void updateMembers() {
      ReadAction.runBlocking(() -> {
        if (!myClass.isValid()) return;
        List<PsiElementClassMember<?>> allMembers = List.of(JavaGenerateToStringActionHandler.buildMembersToShow(myClass));
        if (allMembers.equals(myAllMembers)) return;
        List<PsiElementClassMember<?>> selection = new ArrayList<>(ContainerUtil.filter(members, allMembers::contains));
        for (PsiElementClassMember<?> member : allMembers) {
          if (!myAllMembers.contains(member) && member.getElement().getContainingClass() == myClass) {
            selection.add(member);
          }
        }
        myAllMembers = allMembers;
        members = selection;
      });
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      updateMembers();
      TemplatesManager templatesManager = ToStringTemplatesManager.getInstance();
      List<OptDropdown.Option> templateOptions = createTemplateOptions(templatesManager.getAllTemplates(), myUnavailableClasses);
      if (!ContainerUtil.exists(templateOptions, option -> option.key().equals(template))) {
        // The template settings removed or renamed the selected template.
        template = chooseTemplate(templatesManager, myUnavailableClasses);
      }
      List<OptRegularComponent> components = new ArrayList<>();
      if (!myAllMembers.isEmpty()) {
        components.add(OptPane.multiSelector("members", myAllMembers, OptMultiSelector.SelectionMode.MULTIPLE_OR_EMPTY));
      }
      components.add(
        OptPane.horizontalStack(
          OptPane.dropdown("template", JavaBundle.message("generate.tostring.template.label"),
                           templateOptions.toArray(OptDropdown.Option[]::new)),
          new JavaConfigurationDialogInfo(JavaConfigurationDialogKind.TO_STRING_TEMPLATE_SETTINGS, myLanguage).button()
        ));
      if (myAnnotationsAvailable) {
        components.add(OptPane.checkbox("insertOverride", JavaBundle.message("insert.override.annotation")));
      }
      return new OptPane(components, "editing.altInsert.tostring");
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

      PsiMethod prototype = createMethod(factory, evaluate(psiClass, List.of(), new HashMap<>(), template, config), psiClass);
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
