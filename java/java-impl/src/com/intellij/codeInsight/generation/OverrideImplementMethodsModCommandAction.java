// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptMultiSelector.OptElement;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.PropertiesComponentOptionControllerProvider;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModTriggerFeatureUsed;
import com.intellij.modcommand.ModUpdateSystemOptions;
import com.intellij.modcommand.Presentation;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
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
 * The form shows the stored values of the JavaDoc checkboxes, and the action stores their new values with {@link ModUpdateSystemOptions}.
 * The override mode lists the methods to override, and also the methods to implement when the user merged the lists in the dialog.
 * The implement mode lists the methods to implement.
 * Unlike {@link JavaOverrideImplementMemberChooser}, the form has no toolbar to sort or to merge the lists.
 * <p>
 * The action can also implement the methods in an enum constant.
 * When the enum constant has no body, the action creates the body.
 * <p>
 * The action can also implement the methods in a {@code new} expression of an abstract class or an interface.
 * The action adds an anonymous class body to the {@code new} expression.
 *
 * @see OverrideImplementUtil#chooseAndOverrideOrImplementMethods
 */
public final class OverrideImplementMethodsModCommandAction implements ModCommandAction {
  private static final String COPY_JAVADOC_OPTION = PropertiesComponentOptionControllerProvider.applicationBindId(MemberChooser.PROP_COPYJAVADOC);
  private static final String GENERATE_JAVADOC_OPTION =
    PropertiesComponentOptionControllerProvider.projectBindId(JavaOverrideImplementMemberChooser.PROP_GENERATE_JAVADOC_OVERRIDE_IMPLEMENT);

  private final boolean myToImplement;
  /**
   * The class, the enum constant or the {@code new} expression to implement the methods in, or null to find the class at the caret.
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
   * The target can be in a JVM language other than Java, for example, in Groovy.
   * A {@link com.intellij.codeInsight.MethodImplementor} of the language generates the methods.
   *
   * @param target the class, the enum constant or the {@code new} expression to implement the methods in
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
    boolean copyJavadoc = PropertiesComponentOptionControllerProvider.getBoolean(classOrEnumConst, COPY_JAVADOC_OPTION);
    boolean generateJavadoc = javadocVisible && PropertiesComponentOptionControllerProvider.getBoolean(classOrEnumConst, GENERATE_JAVADOC_OPTION);
    return new ModTriggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT)
      .andThen(ModCommand.editOptions(
        classOrEnumConst,
        JavaOverrideImplementMemberChooser.getChooserTitle(myToImplement, container.merge()),
        () -> new OverrideImplementOptions(members, container.overrideVisible(), javadocVisible, preselection, copyJavadoc,
                                           generateJavadoc, insertOverride),
        true,
        (element, options) -> generate(element, members, options).andThen(storeOptions(element, options))));
  }

  /**
   * Stores the values of the checkboxes, so the next form shows them, like {@link JavaOverrideImplementMemberChooser} does.
   * The command changes only the values that differ from the stored ones.
   */
  private static @NotNull ModCommand storeOptions(@NotNull PsiElement context, @NotNull OverrideImplementOptions options) {
    ModCommand command = PropertiesComponentOptionControllerProvider.setValue(context, COPY_JAVADOC_OPTION, options.copyJavadoc);
    if (options.myGenerateJavadocVisible) {
      command = command.andThen(PropertiesComponentOptionControllerProvider.setValue(context, GENERATE_JAVADOC_OPTION, options.generateJavadoc));
    }
    return command;
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
      if (copy instanceof PsiNewExpression newExpression) {
        PsiClass anonymousClass = addAnonymousClass(newExpression);
        if (anonymousClass == null) return;
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(updater, anonymousClass, withSubstitutors(selected, anonymousClass),
                                                                     generationOptions, toImplement);
        return;
      }
      PsiClass psiClass = copy instanceof PsiEnumConstant constant ? constant.getOrCreateInitializingClass() : (PsiClass)copy;
      OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(updater, psiClass, selected, generationOptions, toImplement);
    });
  }

  /**
   * @return the anonymous class that replaces {@code newExpression}, or null if the text with the anonymous class body is not a valid
   * {@code new} expression
   */
  private static @Nullable PsiAnonymousClass addAnonymousClass(@NotNull PsiNewExpression newExpression) {
    PsiNewExpression withBody = createWithAnonymousClass(newExpression);
    if (withBody == null) return null;
    return ((PsiNewExpression)newExpression.replace(withBody)).getAnonymousClass();
  }

  private static @Nullable PsiNewExpression createWithAnonymousClass(@NotNull PsiNewExpression newExpression) {
    try {
      PsiExpression expression =
        JavaPsiFacade.getElementFactory(newExpression.getProject()).createExpressionFromText(newExpression.getText() + "{}", newExpression);
      return expression instanceof PsiNewExpression result && result.getAnonymousClass() != null ? result : null;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  /**
   * The candidates of a {@code new} expression have the substitutors of the instantiated class.
   * The methods in the anonymous class need the substitutors of the anonymous class, for example, for {@code new Foo<String>()}.
   */
  private static @NotNull List<PsiMethodMember> withSubstitutors(@NotNull List<PsiMethodMember> members, @NotNull PsiClass anonymousClass) {
    return ContainerUtil.map(members, member -> {
      PsiMethod method = member.getElement();
      PsiClass baseClass = method.getContainingClass();
      if (baseClass == null) return member;
      return new PsiMethodMember(method, TypeConversionUtil.getSuperClassSubstitutor(baseClass, anonymousClass, PsiSubstitutor.EMPTY));
    });
  }

  private @Nullable Target findTarget(@NotNull ActionContext context) {
    if (myTarget != null) {
      PsiElement element = myTarget.getElement();
      if (element == null || !(element.getContainingFile() instanceof PsiClassOwner) || !BaseIntentionAction.canModify(element)) return null;
      Target target = element instanceof PsiEnumConstant constant ? enumConstantTarget(constant) :
                      element instanceof PsiClass psiClass ? classTarget(psiClass) :
                      element instanceof PsiNewExpression newExpression ? newExpressionTarget(newExpression) :
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
   * Gets the methods to implement in a {@code new} expression of an abstract class or an interface.
   * The {@code new} expression has no anonymous class body, and the action adds it.
   */
  private static @Nullable Target newExpressionTarget(@NotNull PsiNewExpression newExpression) {
    if (newExpression.getAnonymousClass() != null || createWithAnonymousClass(newExpression) == null) return null;
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null || !(classReference.resolve() instanceof PsiClass psiClass)) return null;
    Collection<CandidateInfo> candidates =
      new ArrayList<>(OverrideImplementExploreUtil.getMapToOverrideImplement(psiClass, true, false).values());
    JavaOverrideImplementMemberChooserContainer container =
      OverrideImplementUtil.prepareJavaOverrideImplementChooser(newExpression, true, candidates, new ArrayList<>());
    if (container == null) return null;
    return new Target(newExpression, container, true);
  }

  /**
   * @param element     the class, the enum constant without a body, or the {@code new} expression without an anonymous class body
   *                    to insert the methods into
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
