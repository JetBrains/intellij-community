// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.PropertiesComponentOptionControllerProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates a constructor with a {@link ModEditOptions} form instead of the Swing dialogs of {@link GenerateConstructorHandler}.
 * Thus, the action works in each client that shows a {@link ModEditOptions} form, for example, in LSP.
 * <p>
 * The form has a selector of the super class constructors when the super class has more than one accessible constructor.
 * Then the form also has the "Copy JavaDoc" checkbox, and the action stores its value with {@link ModCommand#updateOption}.
 * The form has a selector of the fields to initialize when the class has such fields.
 * The field selector initially selects the final fields that no {@link ImplicitUsageProvider} writes implicitly.
 * <p>
 * A record without an explicit canonical constructor gets the "Compact", "Canonical" and "Custom" radio buttons.
 * The selectors are the children of the "Custom" radio button.
 * Thus, one form replaces the two dialogs of {@link GenerateConstructorHandler}.
 * <p>
 * The action shows no form when the form has no control, and generates the constructor at once.
 * When the class has the constructor already, the action moves the caret to the name of that constructor and shows an error message.
 */
public final class GenerateConstructorModCommandAction implements ModCommandAction {
  private static final String COPY_JAVADOC_OPTION = PropertiesComponentOptionControllerProvider.applicationBindId(MemberChooser.PROP_COPYJAVADOC);

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("generate.constructor.action.name");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return findTargetClass(context) == null ? null : Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiClass aClass = findTargetClass(context);
    if (aClass == null) return ModCommand.nop();

    boolean recordKindVisible = aClass.isRecord() &&
                                JavaPsiRecordUtil.findCanonicalConstructor(aClass) instanceof LightRecordCanonicalConstructor;
    List<PsiMethodMember> superConstructors = findSuperConstructors(aClass);
    List<PsiFieldMember> fields = findFields(aClass);
    int offset = context.offset();
    if (!recordKindVisible && superConstructors.size() <= 1 && fields.isEmpty()) {
      return generate(aClass, offset, new ConstructorOptions(false, superConstructors, fields, false));
    }

    boolean copyJavadoc = superConstructors.size() > 1 && PropertiesComponentOptionControllerProvider.getBoolean(aClass, COPY_JAVADOC_OPTION);
    String title = JavaBundle.message(recordKindVisible ? "generate.record.constructor.title" : "generate.constructor.fields.chooser.title");
    return ModCommand.editOptions(
      aClass,
      title,
      () -> new ConstructorOptions(recordKindVisible, superConstructors, fields, copyJavadoc),
      true,
      (cls, options) -> generate(cls, offset, options).andThen(storeOptions(cls, options)));
  }

  /**
   * @return the class at the caret, like {@code GenerateConstructorAction} finds it,
   * or null when the class is an interface, an anonymous class or an implicitly declared class
   */
  private static @Nullable PsiClass findTargetClass(@NotNull ActionContext context) {
    if (!(context.file() instanceof PsiJavaFile file) || file instanceof PsiCompiledElement || !BaseIntentionAction.canModify(file)) {
      return null;
    }
    PsiClass aClass = OverrideImplementUtil.getContextClass(file, context.offset(), false);
    if (aClass == null || aClass.isInterface() || aClass instanceof SyntheticElement ||
        aClass instanceof PsiAnonymousClass || aClass instanceof PsiImplicitClass) {
      return null;
    }
    return aClass;
  }

  /**
   * @return the constructors of the super class that the class can call, with the substitutor of the super class
   */
  private static @NotNull List<PsiMethodMember> findSuperConstructors(@NotNull PsiClass aClass) {
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null) return List.of();
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
    List<PsiMethodMember> result = new ArrayList<>();
    for (PsiMethod constructor : superClass.getConstructors()) {
      if (resolveHelper.isAccessible(constructor, aClass, null)) {
        result.add(new PsiMethodMember(constructor, substitutor));
      }
    }
    return result;
  }

  /**
   * @return the fields that a constructor can initialize: the instance fields without the final fields with an initializer
   */
  private static @NotNull List<PsiFieldMember> findFields(@NotNull PsiClass aClass) {
    List<PsiFieldMember> result = new ArrayList<>();
    for (PsiField field : aClass.getFields()) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) continue;
      result.add(new PsiFieldMember(field));
    }
    return result;
  }

  /**
   * @return the final fields that no {@link ImplicitUsageProvider} writes implicitly
   */
  private static @NotNull List<PsiFieldMember> preselectFields(@NotNull List<PsiFieldMember> fields) {
    List<ImplicitUsageProvider> providers = ImplicitUsageProvider.EP_NAME.getExtensionList();
    return ContainerUtil.filter(fields, member -> {
      PsiField field = member.getElement();
      return field.hasModifierProperty(PsiModifier.FINAL) && !ContainerUtil.exists(providers, p -> p.isImplicitWrite(field));
    });
  }

  /**
   * Stores the value of the "Copy JavaDoc" checkbox, so the next form shows it, like {@link MemberChooser} does.
   */
  private static @NotNull ModCommand storeOptions(@NotNull PsiClass aClass, @NotNull ConstructorOptions options) {
    if (!options.mySuperConstructorsVisible) return ModCommand.nop();
    return PropertiesComponentOptionControllerProvider.setValue(aClass, COPY_JAVADOC_OPTION, options.copyJavadoc);
  }

  private static @NotNull ModCommand generate(@NotNull PsiClass aClass, int offset, @NotNull ConstructorOptions options) {
    Prototypes result = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> createPrototypes(aClass, options));
    PsiMethod existing = result.existing();
    if (existing != null) {
      PsiElement nameIdentifier = existing.getNameIdentifier();
      return ModCommand.moveTo(nameIdentifier != null ? nameIdentifier : existing)
        .andThen(ModCommand.error(JavaBundle.message("generate.constructor.already.exists")));
    }
    List<GenerationInfo> prototypes = result.constructors();
    return ModCommand.psiUpdate(aClass, (cls, updater) -> insert(cls, offset, prototypes, updater));
  }

  /**
   * Creates the constructors like {@link GenerateConstructorHandler} does.
   * The action creates a constructor for each selected super class constructor.
   * When all these constructors exist already, the action creates a constructor that calls no super class constructor.
   *
   * @return the constructors to insert, or the existing constructor when all of them exist already
   */
  private static @NotNull Prototypes createPrototypes(@NotNull PsiClass aClass, @NotNull ConstructorOptions options) {
    if (options.myRecordKindVisible && options.recordConstructor != RecordConstructorKind.CUSTOM) {
      boolean compact = options.recordConstructor == RecordConstructorKind.COMPACT;
      return new Prototypes(List.of(new PsiGenerationInfo<>(new RecordConstructorMember(aClass, compact).generateRecordConstructor())), null);
    }
    // The order of the selectors, not the order of the selection.
    List<PsiMethodMember> superConstructors = options.mySuperConstructorsVisible
                                              ? ContainerUtil.filter(options.myAllSuperConstructors, options.superConstructors::contains)
                                              : options.myAllSuperConstructors;
    PsiField[] fields = ContainerUtil.map2Array(ContainerUtil.filter(options.myAllFields, options.fields::contains), PsiField.EMPTY_ARRAY,
                                                PsiFieldMember::getElement);
    if (!superConstructors.isEmpty()) {
      PsiClass superClass = aClass.getSuperClass();
      if (superClass != null) {
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
        List<GenerationInfo> constructors = new ArrayList<>();
        for (PsiMethodMember member : superConstructors) {
          PsiMethod baseConstructor = GenerateMembersUtil.substituteGenericMethod(member.getElement(), substitutor, aClass);
          PsiMethod constructor =
            GenerateConstructorHandler.generateConstructorPrototype(aClass, baseConstructor, options.copyJavadoc, fields);
          constructors.add(new PsiGenerationInfo<>(constructor));
        }
        if (findExisting(aClass, constructors) == null) return new Prototypes(constructors, null);
      }
    }
    List<GenerationInfo> constructors =
      List.of(new PsiGenerationInfo<>(GenerateConstructorHandler.generateConstructorPrototype(aClass, null, false, fields)));
    return new Prototypes(constructors, findExisting(aClass, constructors));
  }

  /**
   * @return the physical constructor of the class with the signature of the first constructor,
   * when the class has a physical constructor with the signature of each constructor; otherwise, null
   */
  private static @Nullable PsiMethod findExisting(@NotNull PsiClass aClass, @NotNull List<GenerationInfo> constructors) {
    PsiMethod first = null;
    for (GenerationInfo info : constructors) {
      PsiMethod existing = aClass.findMethodBySignature((PsiMethod)Objects.requireNonNull(info.getPsiMember()), false);
      if (existing == null || !existing.isPhysical()) return null;
      if (first == null) first = existing;
    }
    return first;
  }

  /**
   * The result of {@link #createPrototypes}.
   *
   * @param constructors the constructors to insert
   * @param existing     the constructor to show instead, when the class has each of {@code constructors} already; otherwise, null
   */
  private record Prototypes(@NotNull List<GenerationInfo> constructors, @Nullable PsiMethod existing) {
  }

  /**
   * Inserts the prototypes into the writable copy of the class, like {@link GenerateMembersHandlerBase} does,
   * and puts the caret into the first generated constructor.
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

  /**
   * The kind of the constructor of a record without an explicit canonical constructor.
   */
  private enum RecordConstructorKind {
    COMPACT, CANONICAL, CUSTOM
  }

  /**
   * The state of the form. The default option controller binds a control to the field with the name of its bind ID.
   * <p>
   * The constructor gets the data of the form from a read action.
   * Thus, {@link #getOptionsPane()} needs no PSI access, and the IDE can call it on the EDT.
   */
  private static final class ConstructorOptions implements OptionContainer {
    private final boolean myRecordKindVisible;
    private final boolean mySuperConstructorsVisible;
    private final @NotNull List<PsiMethodMember> myAllSuperConstructors;
    private final @NotNull List<PsiFieldMember> myAllFields;

    @SuppressWarnings("FieldMayBeFinal") private @NotNull RecordConstructorKind recordConstructor = RecordConstructorKind.COMPACT;
    @SuppressWarnings("FieldMayBeFinal") private @NotNull List<PsiMethodMember> superConstructors;
    @SuppressWarnings("FieldMayBeFinal") private @NotNull List<PsiFieldMember> fields;
    @SuppressWarnings("FieldMayBeFinal") private boolean copyJavadoc;

    /**
     * @param allSuperConstructors the accessible constructors of the super class.
     *                             The form shows their selector only when the list has more than one constructor.
     *                             Otherwise, the action uses all of them.
     */
    private ConstructorOptions(boolean recordKindVisible,
                               @NotNull List<PsiMethodMember> allSuperConstructors,
                               @NotNull List<PsiFieldMember> allFields,
                               boolean copyJavadoc) {
      myRecordKindVisible = recordKindVisible;
      mySuperConstructorsVisible = allSuperConstructors.size() > 1;
      myAllSuperConstructors = allSuperConstructors;
      myAllFields = allFields;
      // The Swing dialog selects the first super class constructor.
      superConstructors = mySuperConstructorsVisible ? new ArrayList<>(List.of(allSuperConstructors.getFirst())) : new ArrayList<>();
      fields = new ArrayList<>(preselectFields(allFields));
      this.copyJavadoc = copyJavadoc;
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      List<OptRegularComponent> custom = new ArrayList<>();
      if (mySuperConstructorsVisible) {
        custom.add(OptPane.group(JavaBundle.message("generate.constructor.super.constructors.label"),
                                 OptPane.multiSelector("superConstructors", myAllSuperConstructors,
                                                       OptMultiSelector.SelectionMode.MULTIPLE),
                                 OptPane.checkbox("copyJavadoc", IdeBundle.message("checkbox.copy.javadoc"))));
      }
      if (!myAllFields.isEmpty()) {
        OptMultiSelector fieldSelector = OptPane.multiSelector("fields", myAllFields, OptMultiSelector.SelectionMode.MULTIPLE_OR_EMPTY);
        custom.add(custom.isEmpty() ? fieldSelector : OptPane.group(JavaBundle.message("generate.constructor.fields.label"), fieldSelector));
      }
      if (!myRecordKindVisible) return new OptPane(custom);
      return OptPane.pane(
        OptPane.radioButton("recordConstructor", RecordConstructorKind.COMPACT, JavaErrorBundle.message("record.compact.constructor")),
        OptPane.radioButton("recordConstructor", RecordConstructorKind.CANONICAL, JavaErrorBundle.message("record.canonical.constructor")),
        OptPane.radioButton("recordConstructor", RecordConstructorKind.CUSTOM, ApplicationBundle.message("custom.option"),
                            custom.toArray(OptRegularComponent[]::new)));
    }
  }
}
