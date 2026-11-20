// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.java.generate.exception.GenerateCodeException;
import org.jetbrains.java.generate.template.TemplatesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates getters, setters, or both, with a {@link ModEditOptions} form instead of the Swing dialog of {@link GenerateGetterSetterHandlerBase}.
 * Thus, the action works in each client that shows a {@link ModEditOptions} form, for example, in LSP.
 * <p>
 * The form has a field selector, a template drop-down for each kind of accessor, and the "Copy all annotations" checkbox.
 * The field selector of the setters has only the fields that can have a setter, thus it has no final field.
 * Unlike the dialog, the form has no button to edit the templates.
 * The form does not change the default templates and the default of the checkbox.
 */
public final class GenerateGetterSetterModCommandAction implements ModCommandAction {
  private final @NotNull Mode myMode;

  private GenerateGetterSetterModCommandAction(@NotNull Mode mode) {
    myMode = mode;
  }

  /** @return the action that generates getters */
  public static @NotNull ModCommandAction getters() {
    return new GenerateGetterSetterModCommandAction(Mode.GETTERS);
  }

  /** @return the action that generates setters */
  public static @NotNull ModCommandAction setters() {
    return new GenerateGetterSetterModCommandAction(Mode.SETTERS);
  }

  /** @return the action that generates getters and setters */
  public static @NotNull ModCommandAction gettersAndSetters() {
    return new GenerateGetterSetterModCommandAction(Mode.GETTERS_AND_SETTERS);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message(myMode.myNameKey);
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return findTargetClass(context) == null ? null : Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiClass aClass = findTargetClass(context);
    if (aClass == null) return ModCommand.nop();
    GenerateGetterSetterHandlerBase handler = myMode.createHandler();
    ClassMember[] allMembers = handler.getAllOriginalMembers(aClass);
    if (allMembers == null) return ModCommand.error(handler.getNothingFoundMessage());
    if (allMembers.length == 0) return ModCommand.error(handler.getNothingAcceptedMessage());

    List<FieldElement> members = ContainerUtil.map(allMembers, FieldElement::new);
    // The Swing dialog selects the field at the caret, or else the first field.
    FieldElement preselection = Objects.requireNonNullElse(
      ContainerUtil.find(members, member -> member.contains(context.offset())), members.getFirst());

    boolean annotationsAvailable = aClass.getLanguage() == JavaLanguage.INSTANCE;
    boolean copyAllAnnotations = annotationsAvailable && JavaRefactoringSettings.getInstance().GENERATE_ALL_ANNOTATIONS;
    TemplateChoice getterTemplates = myMode.myGetters ? TemplateChoice.of(GetterTemplatesManager.getInstance()) : null;
    TemplateChoice setterTemplates = myMode.mySetters ? TemplateChoice.of(SetterTemplatesManager.getInstance()) : null;

    int offset = context.offset();
    return ModCommand.editOptions(aClass,
                                  JavaBundle.message(myMode.myTitleKey),
                                  () -> new AccessorOptions(myMode, members, getterTemplates, setterTemplates, annotationsAvailable,
                                                            List.of(preselection), copyAllAnnotations),
                                  true,
                                  (cls, options) -> generate(cls, offset, members, options));
  }

  /**
   * @return the class at the caret, like {@link GenerateMembersHandlerBase} finds it,
   * or null when the class is an interface or has no field for the accessors of the mode
   */
  private @Nullable PsiClass findTargetClass(@NotNull ActionContext context) {
    if (!(context.file() instanceof PsiJavaFile file) || file instanceof PsiCompiledElement) return null;
    PsiClass aClass = OverrideImplementUtil.getContextClass(file, context.offset(), false);
    if (aClass == null || aClass.isInterface() || aClass instanceof SyntheticElement) return null;
    return myMode.createHandler().hasMembers(aClass) ? aClass : null;
  }

  private @NotNull ModCommand generate(@NotNull PsiClass aClass,
                                       int offset,
                                       @NotNull List<FieldElement> allMembers,
                                       @NotNull AccessorOptions options) {
    GetterSetterGenerationOptions generationOptions =
      new GetterSetterGenerationOptions(options.copyAllAnnotations, options.getterTemplate, options.setterTemplate);
    // The order of the field selector, not the order of the selection.
    List<ClassMember> selected = ContainerUtil.map(ContainerUtil.filter(allMembers, options.members::contains), FieldElement::member);
    List<GenerationInfo> prototypes = new ArrayList<>();
    try {
      DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
        for (ClassMember member : selected) {
          if (myMode.myGetters) {
            ContainerUtil.addAll(prototypes, GenerateGetterHandler.generateGetters(aClass, member, generationOptions));
          }
          if (myMode.mySetters) {
            ContainerUtil.addAll(prototypes, GenerateSetterHandler.generateSetters(aClass, member, generationOptions));
          }
        }
      });
    }
    catch (GenerateCodeException e) {
      return ModCommand.error(Objects.requireNonNullElse(e.getMessage(), myMode.createHandler().getNothingFoundMessage()));
    }
    if (prototypes.isEmpty()) {
      return ModCommand.error(myMode.createHandler().getNothingFoundMessage());
    }

    ModCommand command = ModCommand.psiUpdate(aClass, (cls, updater) -> insert(cls, offset, prototypes, updater));
    if (myMode == Mode.GETTERS_AND_SETTERS && ContainerUtil.exists(selected, member -> member instanceof PsiFieldMember fieldMember &&
                                                                                       GetterSetterPrototypeProvider.isReadOnlyProperty(fieldMember.getElement()))) {
      command = command.andThen(ModCommand.info(JavaBundle.message("generate.getter.and.setter.error.setters.for.read.only.not.generated")));
    }
    return command;
  }

  /**
   * Inserts the prototypes into the writable copy of the class, like {@link GenerateMembersHandlerBase} does,
   * and puts the caret into the first generated method.
   */
  private static void insert(@NotNull PsiClass aClass, int offset, @NotNull List<GenerationInfo> prototypes, @NotNull ModPsiUpdater updater) {
    List<GenerationInfo> inserted = GenerateMembersUtil.insertMembersAtOffset(aClass, offset, prototypes);
    CodeStyleManager manager = CodeStyleManager.getInstance(aClass.getProject());
    for (GenerationInfo info : inserted) {
      PsiMember member = info.getPsiMember();
      if (member != null) {
        manager.reformat(member);
      }
    }
    if (!inserted.isEmpty()) {
      inserted.getFirst().positionCaret(updater, false);
    }
  }

  private enum Mode {
    GETTERS("generate.getter.action.name", "generate.getter.fields.chooser.title", true, false),
    SETTERS("generate.setter.action.name", "generate.setter.fields.chooser.title", false, true),
    GETTERS_AND_SETTERS("generate.getter.setter.action.name", "generate.getter.setter.title", true, true);

    private final @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String myNameKey;
    private final @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String myTitleKey;
    private final boolean myGetters;
    private final boolean mySetters;

    Mode(@PropertyKey(resourceBundle = JavaBundle.BUNDLE) String nameKey,
         @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String titleKey,
         boolean getters,
         boolean setters) {
      myNameKey = nameKey;
      myTitleKey = titleKey;
      myGetters = getters;
      mySetters = setters;
    }

    /**
     * @return a new handler. A handler has the state of its dialog, thus the action does not keep one.
     */
    @NotNull GenerateGetterSetterHandlerBase createHandler() {
      return switch (this) {
        case GETTERS -> new GenerateGetterHandler();
        case SETTERS -> new GenerateSetterHandler();
        case GETTERS_AND_SETTERS -> new GenerateGetterAndSetterHandler();
      };
    }

    /**
     * @return the label of the template drop-down. The Swing dialog names the kind of accessor only when it has two drop-downs.
     */
    @NotNull @NlsContexts.Label String templateLabel(boolean getter) {
      if (this != GETTERS_AND_SETTERS) return JavaBundle.message("generate.equals.hashcode.template");
      return JavaBundle.message(getter ? "generate.getter.template" : "generate.setter.template");
    }
  }

  /**
   * The templates of a {@link TemplatesManager} for a drop-down.
   *
   * @param options         an option for each template. The key and the label are the name of the template.
   * @param defaultTemplate the name of the default template of the manager
   */
  private record TemplateChoice(@NotNull List<OptDropdown.Option> options, @NotNull String defaultTemplate) {
    static @NotNull TemplateChoice of(@NotNull TemplatesManager manager) {
      List<OptDropdown.Option> options = ContainerUtil.map(manager.getAllTemplates(), t -> OptPane.option(t.getName(), t.getName()));
      return new TemplateChoice(options, manager.getDefaultTemplate().getName());
    }
  }

  /**
   * An element of the field selector.
   */
  private record FieldElement(@NotNull ClassMember member) implements OptMultiSelector.OptElement {
    @Override
    public @NotNull String getText() {
      return member.getText();
    }

    boolean contains(int offset) {
      if (!(member instanceof PsiElementClassMember<?> elementMember)) return false;
      TextRange range = elementMember.getElement().getTextRange();
      return range != null && range.contains(offset);
    }
  }

  /**
   * The state of the form. The default option controller binds a control to the field with the name of its bind ID.
   * <p>
   * The constructor gets the data of the form from a read action.
   * Thus, {@link #getOptionsPane()} needs no PSI access, and the IDE can call it on the EDT.
   */
  private static final class AccessorOptions implements OptionContainer {
    private final @NotNull Mode myMode;
    private final @NotNull List<FieldElement> myAllMembers;
    private final @Nullable TemplateChoice myGetterTemplates;
    private final @Nullable TemplateChoice mySetterTemplates;
    private final boolean myAnnotationsAvailable;

    @SuppressWarnings("FieldMayBeFinal") private @NotNull List<FieldElement> members;
    @SuppressWarnings("FieldMayBeFinal") private @Nullable String getterTemplate;
    @SuppressWarnings("FieldMayBeFinal") private @Nullable String setterTemplate;
    @SuppressWarnings("FieldMayBeFinal") private boolean copyAllAnnotations;

    private AccessorOptions(@NotNull Mode mode,
                            @NotNull List<FieldElement> allMembers,
                            @Nullable TemplateChoice getterTemplates,
                            @Nullable TemplateChoice setterTemplates,
                            boolean annotationsAvailable,
                            @NotNull List<FieldElement> members,
                            boolean copyAllAnnotations) {
      myMode = mode;
      myAllMembers = allMembers;
      myGetterTemplates = getterTemplates;
      mySetterTemplates = setterTemplates;
      myAnnotationsAvailable = annotationsAvailable;
      this.members = new ArrayList<>(members);
      this.getterTemplate = getterTemplates == null ? null : getterTemplates.defaultTemplate();
      this.setterTemplate = setterTemplates == null ? null : setterTemplates.defaultTemplate();
      this.copyAllAnnotations = copyAllAnnotations;
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      List<OptRegularComponent> components = new ArrayList<>();
      components.add(OptPane.multiSelector("members", myAllMembers, OptMultiSelector.SelectionMode.MULTIPLE));
      if (myGetterTemplates != null) {
        components.add(OptPane.dropdown("getterTemplate", myMode.templateLabel(true),
                                        myGetterTemplates.options().toArray(OptDropdown.Option[]::new)));
      }
      if (mySetterTemplates != null) {
        components.add(OptPane.dropdown("setterTemplate", myMode.templateLabel(false),
                                        mySetterTemplates.options().toArray(OptDropdown.Option[]::new)));
      }
      if (myAnnotationsAvailable) {
        components.add(OptPane.checkbox("copyAllAnnotations", JavaBundle.message("generate.getter.setter.generate.all.annotations"))
                         .description(JavaBundle.message("generate.getter.setter.generate.all.annotations.tooltip")));
      }
      return new OptPane(components);
    }
  }
}
