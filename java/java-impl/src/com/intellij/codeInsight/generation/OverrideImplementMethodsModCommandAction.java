// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptMultiSelector.OptElement;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.ide.IdeBundle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.Presentation;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInsight.generation.JavaOverrideImplementMemberChooser.JavaOverrideImplementMemberChooserContainer;

/**
 * Overrides or implements the methods of the super-classes with a {@link ModEditOptions} form instead of a Swing dialog.
 * Thus, the action works in each client that shows a {@link ModEditOptions} form, for example, in LSP.
 * <p>
 * The form has a member selector and the "Copy JavaDoc", "Generate missing JavaDoc" and "Insert @Override" checkboxes.
 * The override mode lists the methods to override, and also the methods to implement when the user merged the lists in the dialog.
 * The implement mode lists the methods to implement.
 * Unlike {@link JavaOverrideImplementMemberChooser}, the form has no toolbar to sort or to merge the lists.
 * <p>
 * The action can also implement the methods in an enum constant.
 * When the enum constant has no body, the action creates the body.
 *
 * @see OverrideImplementUtil#chooseAndOverrideOrImplementMethods
 */
public final class OverrideImplementMethodsModCommandAction implements ModCommandAction {
  private final boolean myToImplement;
  /**
   * The class or the enum constant to implement the methods in, or null to find the class at the caret.
   */
  private final @Nullable SmartPsiElementPointer<PsiElement> myTarget;

  private OverrideImplementMethodsModCommandAction(boolean toImplement, @Nullable PsiElement target) {
    myToImplement = toImplement;
    myTarget = target == null ? null : SmartPointerManager.createPointer(target);
  }

  /**
   * @return the action that overrides the methods in the class at the caret
   */
  public static @NotNull ModCommandAction override() {
    return new OverrideImplementMethodsModCommandAction(false, null);
  }

  /**
   * @return the action that implements the methods in the class or in the enum constant at the caret
   */
  public static @NotNull ModCommandAction implement() {
    return new OverrideImplementMethodsModCommandAction(true, null);
  }

  /**
   * @param target the class or the enum constant to implement the methods in
   * @return the action that implements the methods in {@code target}
   */
  public static @NotNull ModCommandAction implement(@NotNull PsiElement target) {
    return new OverrideImplementMethodsModCommandAction(true, target);
  }

  @Override
  public @NotNull String getFamilyName() {
    if (myTarget != null) return QuickFixBundle.message("implement.methods.fix");
    return JavaBundle.message(myToImplement ? "action.implement.methods" : "action.override.methods");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return findTarget(context) == null ? null : Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    Target target = findTarget(context);
    if (target == null) return ModCommand.nop();
    JavaOverrideImplementMemberChooserContainer container = target.container();
    List<PsiMethodMember> members = target.members();
    ClassMember[] selectElements = container.selectElements();
    List<ClassMember> preselection = selectElements == null ? List.of() : Arrays.asList(selectElements);
    boolean insertOverride = container.overrideVisible() &&
                             JavaCodeStyleSettings.getInstance(container.file()).INSERT_OVERRIDE_ANNOTATION;
    boolean javadocVisible = container.file().getLanguage().is(JavaLanguage.INSTANCE);
    PsiElement classOrEnumConst = target.element();
    return ModCommand.editOptions(
      classOrEnumConst,
      JavaOverrideImplementMemberChooser.getChooserTitle(myToImplement, container.merge()),
      () -> new OverrideImplementOptions(members, container.overrideVisible(), javadocVisible, preselection, false, false, insertOverride),
      true,
      (element, options) -> generate(element, members, options));
  }

  private @NotNull ModCommand generate(@NotNull PsiElement element,
                                       @NotNull List<PsiMethodMember> allMembers,
                                       @NotNull OverrideImplementOptions options) {
    // Keep the order of the list, not the order of the selection.
    List<PsiMethodMember> selected = ContainerUtil.filter(allMembers, options.members::contains);
    if (selected.isEmpty()) return ModCommand.nop();
    OverrideOrImplementOptions generationOptions = new OverrideOrImplementOptions()
      .copyJavadoc(options.copyJavadoc)
      .generateJavadoc(options.generateJavadoc)
      .insertOverrideWherePossible(options.insertOverride);
    boolean toImplement = myToImplement;
    return ModCommand.psiUpdate(element, (copy, updater) -> {
      PsiClass psiClass = copy instanceof PsiEnumConstant constant ? constant.getOrCreateInitializingClass() : (PsiClass)copy;
      OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(updater, psiClass, selected, generationOptions, toImplement);
    });
  }

  private @Nullable Target findTarget(@NotNull ActionContext context) {
    if (myTarget != null) {
      PsiElement element = myTarget.getElement();
      if (element == null || !(element.getContainingFile() instanceof PsiJavaFile) || !BaseIntentionAction.canModify(element)) return null;
      Target target = element instanceof PsiEnumConstant constant ? enumConstantTarget(constant) :
                      element instanceof PsiClass psiClass ? classTarget(psiClass) :
                      null;
      return target == null || target.members().isEmpty() ? null : target;
    }
    if (!(context.file() instanceof PsiJavaFile javaFile) || !BaseIntentionAction.canModify(javaFile)) return null;
    boolean allowInterface = !myToImplement || PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, javaFile);
    PsiClass psiClass = OverrideImplementUtil.getContextClass(javaFile, context.offset(), allowInterface);
    if (psiClass == null) return null;
    if (myToImplement) {
      // The caret is on an enum constant, but not in its body.
      // Then the enum constant gets the methods, because the enum class has its abstract methods already.
      PsiEnumConstant constant = findEnumConstant(context);
      if (constant != null && constant.getContainingClass() == psiClass) {
        Target target = enumConstantTarget(constant);
        if (target != null && target.container().onlyPrimary().length > 0) return target;
      }
    }
    Target target = classTarget(psiClass);
    if (target == null || target.members().isEmpty()) return null;
    // Show the action only when the class has a method that it must implement.
    if (myToImplement && target.container().onlyPrimary().length == 0) return null;
    return target;
  }

  private static @Nullable PsiEnumConstant findEnumConstant(@NotNull ActionContext context) {
    PsiEnumConstant constant = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiEnumConstant.class);
    return constant != null ? constant : PsiTreeUtil.getParentOfType(context.findLeafOnTheLeft(), PsiEnumConstant.class);
  }

  private @Nullable Target classTarget(@NotNull PsiClass psiClass) {
    JavaOverrideImplementMemberChooserContainer container = OverrideImplementUtil.prepareChooser(psiClass, myToImplement);
    return container == null ? null : new Target(psiClass, container, myToImplement);
  }

  /**
   * Gets the methods to implement like {@link com.intellij.codeInsight.daemon.impl.quickfix.ImplementMethodsFix}.
   * An enum constant without a body gets the abstract methods of the enum class, the own methods of the enum class included.
   */
  private static @Nullable Target enumConstantTarget(@NotNull PsiEnumConstant constant) {
    PsiEnumConstantInitializer initializer = constant.getInitializingClass();
    PsiClass psiClass = initializer != null ? initializer : constant.getContainingClass();
    if (psiClass == null) return null;
    Collection<CandidateInfo> candidates =
      new ArrayList<>(OverrideImplementExploreUtil.getMapToOverrideImplement(psiClass, true, initializer != null).values());
    JavaOverrideImplementMemberChooserContainer container =
      OverrideImplementUtil.prepareJavaOverrideImplementChooser(constant, true, candidates, new ArrayList<>());
    if (container == null) return null;
    return new Target(initializer != null ? initializer : constant, container, true);
  }

  /**
   * @param element     the class or the enum constant without a body to insert the methods into
   * @param container   the data that the dialog shows
   * @param toImplement true for the implement mode
   */
  private record Target(@NotNull PsiElement element, @NotNull JavaOverrideImplementMemberChooserContainer container, boolean toImplement) {
    /**
     * @return the members to show, like {@link JavaOverrideImplementMemberChooser} without sorting by overriding
     */
    @NotNull List<PsiMethodMember> members() {
      return Arrays.asList(toImplement || container.merge() ? container.all() : container.onlyPrimary());
    }
  }

  /**
   * The state of the form. The default option controller binds a control to the field with the name of its bind ID.
   * <p>
   * The constructor gets the data of the form from a read action.
   * Thus, {@link #getOptionsPane()} needs no PSI access, and the IDE can call it on the EDT.
   */
  private static final class OverrideImplementOptions implements OptionContainer {
    private final @NotNull List<? extends OptElement> myAllMembers;
    private final boolean myOverrideVisible;
    private final boolean myGenerateJavadocVisible;

    @SuppressWarnings("FieldMayBeFinal") private @NotNull List<? extends OptElement> members;
    @SuppressWarnings("FieldMayBeFinal") private boolean copyJavadoc;
    @SuppressWarnings("FieldMayBeFinal") private boolean generateJavadoc;
    @SuppressWarnings("FieldMayBeFinal") private boolean insertOverride;

    private OverrideImplementOptions(@NotNull List<? extends OptElement> allMembers,
                                     boolean overrideVisible,
                                     boolean generateJavadocVisible,
                                     @NotNull List<? extends OptElement> members,
                                     boolean copyJavadoc,
                                     boolean generateJavadoc,
                                     boolean insertOverride) {
      myAllMembers = allMembers;
      myOverrideVisible = overrideVisible;
      myGenerateJavadocVisible = generateJavadocVisible;
      this.members = new ArrayList<>(members);
      this.copyJavadoc = copyJavadoc;
      this.generateJavadoc = generateJavadoc;
      this.insertOverride = insertOverride;
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      List<OptRegularComponent> components = new ArrayList<>();
      components.add(OptPane.multiSelector("members", myAllMembers, OptMultiSelector.SelectionMode.MULTIPLE_OR_EMPTY));
      components.add(OptPane.checkbox("copyJavadoc", IdeBundle.message("checkbox.copy.javadoc")));
      if (myGenerateJavadocVisible) {
        components.add(OptPane.checkbox("generateJavadoc", JavaBundle.message("methods.to.override.generate.javadoc")));
      }
      if (myOverrideVisible) {
        components.add(OptPane.checkbox("insertOverride", JavaBundle.message("insert.override.annotation")));
      }
      return new OptPane(components);
    }
  }
}
