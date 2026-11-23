// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public class RenameJavaMethodProcessor extends RenameJavaMemberProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaMethodProcessor.class);

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PsiMethod && !(element instanceof LightMethodBuilder);
  }

  @Override
  public void renameElement(@NotNull PsiElement psiElement,
                            @NotNull String newName,
                            UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) {
    PsiMethod method = (PsiMethod) psiElement;
    Set<PsiMethod> methodAndOverriders = new HashSet<>();
    Set<PsiClass> containingClasses = new HashSet<>();
    LinkedHashSet<PsiElement> renamedReferences = new LinkedHashSet<>();
    List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<>();
    List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<>();

    methodAndOverriders.add(method);
    containingClasses.add(method.getContainingClass());

    // do actual rename of overriding/implementing methods and of references to all them
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;

      if (usage instanceof MemberHidesStaticImportUsageInfo hidesStatic) {
        staticImportHides.add(hidesStatic);
      }
      else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
        PsiReference reference = element.getReference();
        if (reference == null) continue;
        PsiMethod resolved = (PsiMethod)reference.resolve();
        outerHides.add(new MemberHidesOuterMemberUsageInfo(element, resolved));
      }
      else if (!(element instanceof PsiMethod overrider)) {
        final PsiReference ref = usage instanceof MoveRenameUsageInfo ? usage.getReference() : element.getReference();
        if (ref instanceof PsiImportStaticReferenceElement staticRef && staticRef.multiResolve(false).length > 1) {
          continue;
        }
        if (ref != null) {
          PsiElement e = processRef(ref, newName);
          if (e != null) {
            renamedReferences.add(e);
          }
        }
      }
      else {
        methodAndOverriders.add(overrider);
        containingClasses.add(overrider.getContainingClass());
      }
    }

    // do actual rename of method
    method.setName(newName);
    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();
      if (element instanceof PsiMethod m) {
        m.setName(newName);
      }
    }
    if (listener != null) {
      listener.elementRenamed(method);
    }

    for (PsiElement element: renamedReferences) {
      fixNameCollisionsWithInnerClassMethod(element, newName, methodAndOverriders, containingClasses,
                                            method.hasModifierProperty(PsiModifier.STATIC));
    }
    qualifyOuterMemberReferences(outerHides);
    qualifyStaticImportReferences(staticImportHides);

    if (!method.isConstructor() && !(method instanceof LightElement) && JavaPsiRecordUtil.getRecordComponentForAccessor(method) == null
        && method.findDeepestSuperMethods().length == 0) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, CommonClassNames.JAVA_LANG_OVERRIDE);
      if (annotation != null) {
        annotation.delete();
      }
    }
  }

  /**
   * handles rename of refs
   */
  protected @Nullable PsiElement processRef(PsiReference ref, String newName) {
    return ref.handleElementRename(newName);
  }

  private static void fixNameCollisionsWithInnerClassMethod(PsiElement element,
                                                            String newName,
                                                            Set<PsiMethod> methodAndOverriders,
                                                            Set<PsiClass> containingClasses,
                                                            boolean isStatic) {
    if (!(element instanceof PsiReferenceExpression ref) || ref.getQualifierExpression() != null) return;
    PsiElement elem = ref.resolve();

    if (elem instanceof PsiMethod actualMethod) {
      if (actualMethod instanceof LightRecordMethod || actualMethod instanceof LightRecordCanonicalConstructor) return;
      if (!methodAndOverriders.contains(actualMethod)) {
        PsiClass outerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        while (outerClass != null) {
          PsiClass finalOuterClass = outerClass;
          if (ContainerUtil.exists(containingClasses, psiClass -> InheritanceUtil.isInheritorOrSelf(finalOuterClass, psiClass, true))) {
            qualifyMember(element, newName, outerClass, isStatic);
            break;
          }
          outerClass = PsiTreeUtil.getParentOfType(outerClass, PsiClass.class);
        }
      }
    }
  }

  @Override
  public @NotNull Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                          @NotNull SearchScope searchScope,
                                                          boolean searchInCommentsAndStrings) {
    return MethodReferencesSearch.search((PsiMethod)element, searchScope, true).findAll();
  }

  @Override
  public void findCollisions(@NotNull PsiElement element,
                             @NotNull String newName,
                             @NotNull Map<? extends PsiElement, String> allRenames,
                             @NotNull List<UsageInfo> result) {
    final PsiMethod methodToRename = (PsiMethod)element;
    findSubmemberHidesMemberCollisions(methodToRename, newName, result);
    findMemberHidesOuterMemberCollisions((PsiMethod) element, newName, result);
    findCollisionsAgainstNewName(methodToRename, newName, result);
    findHidingMethodWithOtherSignature(methodToRename, newName, result);
  }

  private void findHidingMethodWithOtherSignature(PsiMethod methodToRename, String newName, List<UsageInfo> result) {
    final PsiClass containingClass = methodToRename.getContainingClass();
    if (containingClass != null) {
      final PsiMethod prototype = getPrototypeWithNewName(methodToRename, newName);
      if (prototype == null || containingClass.findMethodBySignature(prototype, true) != null) return;

      final PsiMethod[] methodsByName = containingClass.findMethodsByName(newName, true);
      if (methodsByName.length > 0) {

        for (UsageInfo info : result) {
          final PsiElement element = info.getElement();
          if (element instanceof PsiReferenceExpression ref) {
            if (ref.resolve() == methodToRename) {
              final PsiElement parent = element.getParent();
              final PsiReferenceExpression copyRef;
              if (parent instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression copy = (PsiMethodCallExpression)JavaPsiFacade.getElementFactory(element.getProject())
                  .createExpressionFromText(parent.getText(), element);
                copyRef = copy.getMethodExpression();
              }
              else {
                LOG.assertTrue(element instanceof PsiMethodReferenceExpression, element.getText());
                copyRef = (PsiReferenceExpression)element.copy();
              }
              final PsiReferenceExpression expression = (PsiReferenceExpression)processRef(copyRef, newName);
              if (expression == null) continue;
              final JavaResolveResult resolveResult = expression.advancedResolve(true);
              final PsiMember resolveResultElement = (PsiMember)resolveResult.getElement();
              if (resolveResult.isValidResult() && resolveResultElement != null) {
                result.add(new UnresolvableCollisionUsageInfo(element, methodToRename) {
                  @Override
                  public String getDescription() {
                    return JavaRefactoringBundle.message("method.call.would.be.linked.to.0.after.rename",
                                                         RefactoringUIUtil.getDescription(resolveResultElement, true));
                  }
                });
                break;
              }
            }
          }
        }
      }
    }
  }

  private static PsiMethod getPrototypeWithNewName(PsiMethod methodToRename, String newName) {
    final PsiMethod prototype = (PsiMethod)methodToRename.copy();
    if (prototype != null) {
      try {
        prototype.setName(newName);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
    return prototype;
  }

  @Override
  public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String newName,
                                        @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    final PsiMethod refactoredMethod = (PsiMethod)element;
    if (newName.equals(refactoredMethod.getName())) return;
    final PsiMethod prototype = getPrototypeWithNewName(refactoredMethod, newName);
    if (prototype == null) return;

    ConflictsUtil.checkMethodConflicts(
      refactoredMethod.getContainingClass(),
      refactoredMethod,
      prototype,
      conflicts);
  }

  /**
   * Collects the overriders to rename, and asks the user nothing.
   * <p>
   * It runs on EDT for a rename with a user, and on a background thread inside a read action for a
   * rename with no user. The search goes under a modal progress only on EDT, for the reason
   * {@code SuperMethodWarningUtil.getSuperMethods} states.
   */
  @Override
  public void prepareRenaming(@NotNull PsiElement element,
                              @NotNull String newName,
                              @NotNull Map<PsiElement, String> allRenames,
                              @NotNull SearchScope scope) {
    final PsiMethod method = (PsiMethod) element;

    for (PsiMethod sibling : SuperMethodWarningUtil.getSiblings(method)) {
      //append all super methods
      if (sibling != method) {
        allRenames.put(sibling, newName);
      }

      ThrowableComputable<Collection<PsiMethod>, RuntimeException> search =
        () -> OverridingMethodsSearch.search(sibling, scope, true).findAll();
      Collection<PsiMethod> allOverriders = EDT.isCurrentThreadEdt()
                                            ? ProgressManager.getInstance().runProcessWithProgressSynchronously(
                                              search, RefactoringBundle.message("searching.for.overrides"), true, element.getProject())
                                            : search.compute();

      for (PsiMethod overrider : allOverriders) {
        if (overrider instanceof PsiMirrorElement mirror && mirror.getPrototype() instanceof PsiMethod m) {
          overrider = m;
        }

        PsiMember realMember = overrider instanceof LightRecordMethod lrm ? lrm.getRecordComponent() : overrider;
        if (realMember instanceof SyntheticElement) continue;

        final String overriderName = realMember.getName();
        final String baseName = sibling.getName();
        final String newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName);
        if (newOverriderName != null) {
          RenameUtil.assertNonCompileElement(realMember);
          allRenames.put(realMember, newOverriderName);
        }
      }
    }
  }

  @Override
  public @NonNls String getHelpID(PsiElement element) {
    return HelpID.RENAME_METHOD;
  }

  @Override
  public boolean isToSearchInComments(@NotNull PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
  }

  @Override
  public void setToSearchInComments(@NotNull PsiElement element, boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = enabled;
  }

  @Override
  public @Nullable PsiElement substituteElementToRename(@NotNull PsiElement element, Editor editor) {
    return substituteElementToRename(element, editor, true);
  }

  /**
   * The element to rename instead of {@code element}.
   *
   * @param askUser false answers the base-method question with the base method and its whole
   *                hierarchy, and reports an error through a return value instead of a dialog. A
   *                rename with no user passes false.
   */
  @ApiStatus.Internal
  public @Nullable PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor, boolean askUser) {
    return substituteElementToRename(element, editor, askUser, true);
  }

  /**
   * @param renameBaseMethod the answer to the base-method question when {@code askUser} is false: false renames {@code element} alone, as
   *                         the answer "no" of the user does
   */
  @ApiStatus.Internal
  public static @Nullable PsiElement substituteElementToRename(@NotNull PsiElement element,
                                                               @Nullable Editor editor,
                                                               boolean askUser,
                                                               boolean renameBaseMethod) {
    PsiMethod psiMethod = (PsiMethod)element;
    if (psiMethod.isConstructor()) {
      PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return null;
      if (Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        if (askUser) {
          return !PsiElementRenameHandler.canRename(containingClass.getProject(), editor, containingClass) ? null : containingClass;
        }
        // canRename shows an error hint. The caller of the headless path reports the message itself.
        return PsiElementRenameHandler.getRenameErrorMessage(containingClass.getProject(), null, containingClass) == null
               ? containingClass : null;
      }
    }
    PsiRecordComponent recordComponent = JavaPsiRecordUtil.getRecordComponentForAccessor(psiMethod);
    if (recordComponent != null) {
      return recordComponent;
    }
    PsiMethod[] superMethods = askUser
                               ? SuperMethodWarningUtil.checkSuperMethods(psiMethod, RefactoringBundle.message("to.rename"))
                               : renameBaseMethod ? SuperMethodWarningUtil.getTargetMethodCandidates(psiMethod, List.of())
                                                  : new PsiMethod[]{psiMethod};
    if (superMethods.length == 0) return null;
    SuperMethodWarningUtil.putSiblings(superMethods, superMethods[0]);
    return superMethods[0];
  }

  /**
   * @return the question about the base method of {@code method}, if the rename asks it, as {@link SuperMethodWarningUtil#checkSuperMethods}
   * does: the method has a base method, or a sibling method that a subclass inherits for it; else null
   */
  private static @Nullable ModPlanQuestion baseMethodQuestion(@NotNull SymbolPointer subject, @NotNull PsiMethod method) {
    if (method.isConstructor() || JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) return null;
    PsiMethod[] candidates = SuperMethodWarningUtil.getTargetMethodCandidates(method, List.of());
    if (candidates.length == 1 && candidates[0] == method) return null;
    String baseClassNames = StringUtil.join(candidates, RenameJavaMethodProcessor::classNameOf, ", ");
    ModPlanQuestion.Form fallback = ModPlanQuestion.Form.yesNo(
      ModPlanJavaRenameOptions.RENAME_BASE_METHOD,
      JavaBundle.message("rename.the.base.methods.in.0", baseClassNames, candidates.length),
      JavaBundle.message("rename.only.the.method.in.0", classNameOf(method)));
    return new BaseMethodQuestion(subject, fallback);
  }

  /** @return the qualified name of the class of {@code method}, else its short name */
  private static @NotNull String classNameOf(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return "";
    return StringUtil.notNullize(containingClass.getQualifiedName(), StringUtil.notNullize(containingClass.getName()));
  }

  /**
   * The question "the method overrides a method, rename the base method instead?", in the dialog of the legacy rename. Its answer is
   * {@link ModPlanJavaRenameOptions#RENAME_BASE_METHOD}.
   *
   * @param fallback the form for a client without the dialog of the legacy rename
   */
  private record BaseMethodQuestion(@NotNull SymbolPointer subject, @NotNull ModPlanQuestion.Form fallback)
    implements ModPlanQuestion.Custom {
    @Override
    public @NotNull List<ModPlanOption<?>> options() {
      return List.of(ModPlanJavaRenameOptions.RENAME_BASE_METHOD);
    }

    @Override
    public @Nullable ModPlanOptionValues ask(@NotNull Project project, @NotNull ModPlanOptionValues currentValues) {
      if (!(subject.element() instanceof PsiMethod method)) return currentValues;
      PsiMethod[] superMethods = SuperMethodWarningUtil.checkSuperMethods(method, RefactoringBundle.message("to.rename"));
      if (superMethods.length == 0) return null;
      boolean renamesAlone = superMethods.length == 1 && superMethods[0] == method;
      return currentValues.with(ModPlanJavaRenameOptions.RENAME_BASE_METHOD, !renamesAlone);
    }
  }

  @Override
  public void substituteElementToRename(@NotNull PsiElement element,
                                        @NotNull Editor editor,
                                        @NotNull Pass<? super PsiElement> renameCallback) {
    final PsiMethod psiMethod = (PsiMethod)element;
    if (psiMethod.isConstructor()) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return;
      if (!Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        renameCallback.accept(psiMethod);
        return;
      }
      super.substituteElementToRename(element, editor, renameCallback);
    }
    else {
      PsiRecordComponent recordComponent = JavaPsiRecordUtil.getRecordComponentForAccessor(psiMethod);
      if (recordComponent != null) {
        renameCallback.accept(recordComponent);
        return;
      }
      SuperMethodWarningUtil.checkSuperMethod(psiMethod, new PsiElementProcessor<>() {
        @Override
        public boolean execute(@NotNull PsiMethod method) {
          if (!PsiElementRenameHandler.canRename(method.getProject(), editor, method)) return false;
          renameCallback.accept(method);
          return false;
        }
      }, editor);
    }
  }

  private static void findSubmemberHidesMemberCollisions(PsiMethod method, String newName, List<? super UsageInfo> result) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return;
    Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass).findAll();

    MethodSignature oldSignature = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignature newSignature = MethodSignatureUtil.createMethodSignature(newName, oldSignature.getParameterTypes(),
                                                                             oldSignature.getTypeParameters(),
                                                                             oldSignature.getSubstitutor(),
                                                                             method.isConstructor());
    for (PsiClass inheritor : inheritors) {
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, inheritor, PsiSubstitutor.EMPTY);
      for (PsiMethod conflictingMethod : inheritor.findMethodsByName(newName, false)) {
        if (newSignature.equals(conflictingMethod.getSignature(superSubstitutor))) {
          result.add(new SubmemberHidesMemberUsageInfo(conflictingMethod, method));
          break;
        }
      }
    }
  }

   @Override
   public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull PsiElement element, boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD = enabled;
  }

  @Override
  public @NotNull UsageInfo createUsageInfo(@NotNull PsiElement element, @NotNull PsiReference ref, @NotNull PsiElement referenceElement) {
    return new MoveRenameUsageInfo(referenceElement, ref,
                                   ref.getRangeInElement().getStartOffset(),
                                   ref.getRangeInElement().getEndOffset(),
                                   element,
                                   ref.resolve() == null && !(ref instanceof PsiPolyVariantReference p && p.multiResolve(true).length > 0)) {
      @Override
      public boolean equals(Object o) {
        return super.equals(o) && o instanceof MoveRenameUsageInfo info && element.equals(info.getReferencedElement());
      }

      @Override
      public int hashCode() {
        return 29 * super.hashCode() + element.hashCode();
      }
    };
  }

  /**
   * What {@code renameElement} finds after the write: the method overrides nothing under the new name, so its {@code @Override} goes.
   * Under the new name it overrides a method of a superclass that is renamed to it, or that has it now and is not renamed.
   *
   * <p>The annotation goes, as {@code Foo.foo} is renamed to {@code bar} and {@code Bar.foo} keeps its name:
   * <pre>{@code
   * class Bar { public void foo() {} }
   * class Foo extends Bar { @Override public void foo() {} }   // rename Foo.foo to bar
   * // after
   * class Foo extends Bar { public void bar() {} }
   * }</pre>
   *
   * <p>The annotation stays, as the method that {@code Sub.run} overrides is renamed to the new name too:
   * <pre>{@code
   * class Worker { public void run() {} }   // rename Worker.run to execute
   * class Sub extends Worker { @Override public void run() {} }
   * // after
   * class Worker { public void execute() {} }
   * class Sub extends Worker { @Override public void execute() {} }
   * }</pre>
   *
   * <p>The annotation stays, as a superclass already has a method of the new name:
   * <pre>{@code
   * class A { void execute() {} }
   * class B extends A { void run() {} }
   * class C extends B { @Override void run() {} }   // rename C.run to execute
   * // after
   * class C extends B { @Override void execute() {} }   // it overrides A.execute
   * }</pre>
   */
  @ApiStatus.Internal
  public static @Nullable PsiAnnotation overrideAfterRename(@NotNull PsiMethod method,
                                                            @NotNull String newName,
                                                            @NotNull Map<PsiElement, String> renamed) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || method.isConstructor() || method instanceof LightElement ||
        JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
      return null;
    }
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, CommonClassNames.JAVA_LANG_OVERRIDE);
    if (annotation == null) return null;
    for (PsiMethod superMethod : method.findSuperMethods()) {
      if (newName.equals(renamed.get(superMethod))) return null;
      // A light method of another language, such as a Kotlin function, is renamed by its source, maybe to another name, as a mangled one.
      PsiElement source = superMethod.getNavigationElement();
      if (source != superMethod && renamed.containsKey(source)) return null;
    }
    MethodSignature signature =
      MethodSignatureUtil.createMethodSignature(newName, method.getParameterList(), method.getTypeParameterList(), PsiSubstitutor.EMPTY);
    for (PsiMethod candidate : containingClass.findMethodsByName(newName, true)) {
      PsiClass candidateClass = candidate.getContainingClass();
      if (candidateClass == null || candidateClass == containingClass || renamed.containsKey(candidate) ||
          candidate.hasModifierProperty(PsiModifier.STATIC) || candidate.hasModifierProperty(PsiModifier.PRIVATE)) {
        continue;
      }
      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(candidateClass, containingClass, PsiSubstitutor.EMPTY);
      if (MethodSignatureUtil.isSubsignature(candidate.getSignature(substitutor), signature)) return null;
    }
    return annotation;
  }

  /** The rename on the ModPlan engine of the elements that this processor renames. */
  @ApiStatus.Internal
  public static final class ModPlanJavaMethodRenameProvider extends ModPlanJavaRenameProvider {
    public ModPlanJavaMethodRenameProvider() {
      super(RenameJavaMethodProcessor.class);
    }

    @Override
    protected @NotNull List<ModPlanQuestion> substitutionQuestions(@NotNull SymbolPointer subject, @NotNull PsiElement element) {
      ModPlanQuestion question = element instanceof PsiMethod method ? baseMethodQuestion(subject, method) : null;
      return question == null ? List.of() : List.of(question);
    }

    @Override
    protected @Nullable PsiElement substitute(@NotNull PsiElement element, @NotNull ModPlanOptionValues answers) {
      if (!(element instanceof PsiMethod)) return element;
      return substituteElementToRename(element, null, false, answers.get(ModPlanJavaRenameOptions.RENAME_BASE_METHOD));
    }

    /** As {@code LightRecordMethod.setName}, the implicit accessor of a record component is renamed by the component. */
    @Override
    protected void prepareRenaming(@NotNull PsiElement element,
                                   @NotNull String newName,
                                   @NotNull Map<PsiElement, String> renamed,
                                   @NotNull ModPlanOptionValues answers) {
      super.prepareRenaming(element, newName, renamed, answers);
      if (element instanceof LightRecordMethod accessor) {
        renamed.putIfAbsent(accessor.getRecordComponent(), newName);
      }
    }
  }
}
