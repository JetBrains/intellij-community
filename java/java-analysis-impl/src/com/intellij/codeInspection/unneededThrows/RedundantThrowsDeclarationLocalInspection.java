// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.JavaOverridingMethodUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.psi.PsiModifier.ABSTRACT;

@SuppressWarnings("InspectionDescriptionNotFoundInspection") // delegates
public final class RedundantThrowsDeclarationLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOGGER = Logger.getInstance(RedundantThrowsDeclarationLocalInspection.class.getName());

  @NotNull private final RedundantThrowsDeclarationInspection myGlobalTool;

  @TestOnly
  public RedundantThrowsDeclarationLocalInspection() {
    this(new RedundantThrowsDeclarationInspection());
  }

  public RedundantThrowsDeclarationLocalInspection(@NotNull final RedundantThrowsDeclarationInspection tool) {
    myGlobalTool = tool;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return myGlobalTool.getGroupDisplayName();
  }

  @Override
  @NotNull
  public String getShortName() {
    return myGlobalTool.getShortName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getDisplayName() {
    // this method is required by unit tests to look for "For all 'Redundant 'throws' clause' problems in file"
    // since this local inspection is not defined in JavaAnalysisPlugin.xml and hence has no shortName
    return JavaAnalysisBundle.message("inspection.redundant.throws.display.name");
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new RedundantThrowsVisitor(holder, myGlobalTool.IGNORE_ENTRY_POINTS);
  }

  /**
   * The method extracts throws declarations that are probably redundant from the method's throws list
   * It filters out throws declarations that are either of java.lang.RuntimeException or java.rmi.RemoteException type.
   *
   * @param method            method to extract throws declarations from
   * @param ignoreEntryPoints whether or not to extract throws if the method is an entry point (e.g. public static void main)
   * @return a stream of throws declarations that are candidates for redundant declarations
   */
  @Contract(pure = true)
  static StreamEx<ThrowRefType> getRedundantThrowsCandidates(@Nullable final PsiMethod method, final boolean ignoreEntryPoints) {
    if (method == null) return StreamEx.empty();
    if (method instanceof SyntheticElement) return StreamEx.empty();
    if (ignoreEntryPoints && UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(method)) return StreamEx.empty();
    if (method.hasModifier(JvmModifier.NATIVE)) return StreamEx.empty();
    if (JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass())) return StreamEx.empty();

    final PsiReferenceList throwsList = method.getThrowsList();

    checkInconsistency(throwsList, method.getClass().getSimpleName());

    final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    return StreamEx.of(referenceElements)
      .map(ref -> new ThrowRefType(ref, factory.createType(ref)))
      .filter(ThrowRefType::isCheckedException)
      .filter(p -> !p.isRemoteExceptionInRemoteMethod(method));
  }

  static final class RedundantThrowsVisitor extends JavaElementVisitor {

    private @NotNull final ProblemsHolder myHolder;
    private final boolean myIgnoreEntryPoints;
    private RedundantThrowsVisitor(@NotNull final ProblemsHolder holder, final boolean ignoreEntryPoints) {
      myHolder = holder;
      myIgnoreEntryPoints = ignoreEntryPoints;
    }

    @Override
    public void visitMethod(@NotNull final PsiMethod method) {
      getRedundantThrowsCandidates(method, myIgnoreEntryPoints)
        .filter(throwRefType -> !throwRefType.isThrownIn(method))
        .filter(throwRefType -> !throwRefType.isInOverriddenOf(method))
        .filter(throwRefType -> !throwRefType.isCaught(method))
        .forEach(throwRefType -> {
          final PsiElement reference = throwRefType.myReference;
          final PsiClassType exceptionType = throwRefType.myType;

          final String description = JavaErrorBundle.message("exception.is.never.thrown", JavaHighlightUtil.formatType(exceptionType));
          final RedundantThrowsQuickFix fix = new RedundantThrowsQuickFix(exceptionType.getCanonicalText(), PsiFormatUtil.formatSimple(method));
          myHolder.registerProblem(reference, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
        });
    }

    static final class RedundantThrowsQuickFix extends PsiUpdateModCommandQuickFix {

      @NotNull private final String myMethodName;
      @NotNull private final String myExceptionName;
      private RedundantThrowsQuickFix(@NotNull final String exceptionName, @NotNull final String methodName) {
        myExceptionName = exceptionName;
        myMethodName = methodName;
      }

      @Override
      public @IntentionName @NotNull String getName() {
        final String exceptionName = StringUtil.getShortName(myExceptionName);
        return QuickFixBundle.message("fix.throws.list.remove.exception", exceptionName, myMethodName);
      }

      @Override
      public @IntentionFamilyName @NotNull String getFamilyName() {
        return QuickFixBundle.message("fix.throws.list.family");
      }

      @Override
      protected void applyFix(@NotNull Project project, @NotNull PsiElement elem, @NotNull ModPsiUpdater updater) {
        if (!(elem instanceof PsiJavaCodeReferenceElement)) return;

        final PsiElement maybeMethod = PsiTreeUtil.skipParentsOfType(elem, PsiReferenceList.class);
        if (!(maybeMethod instanceof PsiMethod method)) return;

        getRelatedJavadocThrows((PsiJavaCodeReferenceElement)elem, method.getDocComment())
          .forEach(e -> e.delete());

        new CommentTracker().deleteAndRestoreComments(elem);
      }

      /**
       * The method returns a {@link Stream<PsiDocTag>} of @throws tags from the javadoc that are related to the throw declaration.
       * This method works like this:
       * <ul>
       *   <li>If the javadoc is null, then return {@link Stream#empty()}</li>
       *   <li>If there are no other throws declarations in the throws list but the current one then all the @throws declarations are returned.</li>
       *   <li>All the @throws tags that are subclasses of the current throws declaration are returned except if there are no other throws declarations in the throws list that can be parents of the same @throws tag.</li>
       *   <li>If there are duplicates in the throws list and one of them is being removed then no related @throws tags is returned.</li>
       * </ul>
       * @param currentThrowsRef current throws list's element to get the {@link Stream<PsiDocTag>} for
       * @param comment current javadoc
       * @return a {@link Stream} of {@link PsiDocTag} with related @throws tag from the javadoc
       */
      @NotNull
      static Stream<PsiDocTag> getRelatedJavadocThrows(@NotNull final PsiJavaCodeReferenceElement currentThrowsRef,
                                                       @Nullable final PsiDocComment comment) {
        if (comment == null) return Stream.empty();

        final PsiElement maybeThrowsList = currentThrowsRef.getParent();
        if (!(maybeThrowsList instanceof PsiReferenceList throwsList)) return Stream.empty();

        // return all @throws declarations from javadoc if the last throws declaration in the throws list is getting eliminated.
        if (throwsList.getReferenceElements().length == 1) {
          return StreamEx.of(comment.getTags())
            .filterBy(PsiDocTag::getName, "throws");
        }

        final PsiElement maybeClass = currentThrowsRef.resolve();
        if (!(maybeClass instanceof PsiClass reference)) return Stream.empty();

        final List<PsiClassType> throwsListWithoutCurrent = getThrowsListWithoutCurrent(throwsList, currentThrowsRef);

        final PsiClassType referenceType = PsiTypesUtil.getClassType(reference);
        if (throwsListWithoutCurrent.contains(referenceType)) return Stream.empty();

        final PsiManager manager = reference.getManager();

        final Predicate<PsiDocTag> isTagRelatedToCurrentThrowsRef = tag -> {
          final PsiClass throwsClass = JavaDocUtil.resolveClassInTagValue(tag.getValueElement());
          if (throwsClass == null) return false;
          // either the tag's class is exactly the current throws reference
          // or it's a inheritor of the throws declaration and there are no other parents in the throws list
          return manager.areElementsEquivalent(throwsClass, reference) ||
                 (throwsClass.isInheritor(reference, true) && !isParentInThrowsListPresent(throwsClass, throwsListWithoutCurrent));
        };

        return StreamEx.of(comment.getTags())
          .filterBy(PsiDocTag::getName, "throws")
          .filter(isTagRelatedToCurrentThrowsRef);
      }

      /**
       * Checks if there are classes in the throws list that can be parents of the class
       * @param clazz a class to check if there are parents in the throws list for it
       * @param throwsList a list of throws list
       * @return true if there is at least one element in the list that can be a parent of the class, false otherwise
       */
      private static boolean isParentInThrowsListPresent(@NotNull final PsiClass clazz,
                                                         @NotNull final List<PsiClassType> throwsList) {
        final PsiClassType type = PsiTypesUtil.getClassType(clazz);
        return ContainerUtil.exists(throwsList, e -> e.isAssignableFrom(type));
      }

      /**
       * This method returns the set of throws types as strings declared in the throws list excluding the currently eliminated throws exception.
       *
       * @param throwsList throws list of a method
       * @param currentRef the currently eliminated throws declaration in the throws list
       * @return the list of throws declarations as strings from the throws list excluding the currently eliminated throws declaration
       */
      private static List<PsiClassType> getThrowsListWithoutCurrent(@NotNull final PsiReferenceList throwsList,
                                                                    @NotNull final PsiJavaCodeReferenceElement currentRef) {
        final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();

        checkInconsistency(throwsList);

        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(throwsList.getProject());
        return StreamEx.of(referenceElements)
          .filter(ref -> ref != currentRef)
          .map(factory::createType)
          .toList();
      }
    }
  }

  /**
   * Holder for a throw declaration (either in throws list or javadoc) in a method and its exception class type.
   */
  static final class ThrowRefType {
    @NotNull private final PsiJavaCodeReferenceElement myReference;
    @NotNull private final PsiClassType myType;

    private ThrowRefType(@NotNull final PsiJavaCodeReferenceElement reference, @NotNull final PsiClassType type) {
      myReference = reference;
      myType = type;
    }

    @Contract(pure = true)
    private boolean isCheckedException() {
      return !ExceptionUtil.isUncheckedException(myType);
    }

    @Contract(pure = true)
    private boolean isRemoteExceptionInRemoteMethod(@NotNull final PsiMethod psiMethod) {
      if (!myType.equalsToText("java.rmi.RemoteException")) return false;

      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass == null) return false;

      final JavaPsiFacade instance = JavaPsiFacade.getInstance(containingClass.getProject());
      final PsiClass remote = instance.findClass("java.rmi.Remote", GlobalSearchScope.allScope(containingClass.getProject()));
      return remote != null && containingClass.isInheritor(remote, true);
    }

    @Contract(pure = true)
    private boolean isInOverriddenOf(@NotNull final PsiMethod method) {
      if (!PsiUtil.canBeOverridden(method)) return false;

      final Predicate<PsiMethod> methodContainsThrownExceptions = m -> !ArrayUtil.isEmpty(m.getThrowsList().getReferencedTypes());

      final Stream<PsiMethod> overridingMethods =
        JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method, null, methodContainsThrownExceptions);
      if (overridingMethods == null) return true;

      return overridingMethods.anyMatch(m -> isThrownIn(m) || isInThrowsListOf(m));
    }

    @Contract(pure = true)
    private boolean isThrownIn(@NotNull final PsiMethod method) {
      if (method.hasModifierProperty(ABSTRACT)) return true;
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return true;

      if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return true;

      final PsiCodeBlock body = method.getBody();
      if (body == null) return true;

      final Set<PsiClassType> unhandled = RedundantThrowsGraphAnnotator.getUnhandledExceptions(body, method, containingClass);

      return ContainerUtil.exists(unhandled, myType::isAssignableFrom);
    }

    @Contract(pure = true)
    private boolean isInThrowsListOf(@NotNull final PsiMethod method) {
      return ContainerUtil.exists(method.getThrowsList().getReferencedTypes(), myType::isAssignableFrom);
    }

    boolean isCaught(@NotNull final PsiMethod method) {
      if (method.getUseScope() instanceof GlobalSearchScope) {
        final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(method.getProject());
        final PsiSearchHelper.SearchCostResult search = searchHelper.isCheapEnoughToSearch(method.getName(),
                                                                                           (GlobalSearchScope)method.getUseScope(),
                                                                                           null,
                                                                                           null);
        if (search == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) return false;
        if (search == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return true;
      }

      final Collection<PsiReference> references = ReferencesSearch.search(method).findAll();
      for (PsiReference reference : references) {
        if (!(reference instanceof PsiElement element)) continue;
        final PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        final boolean catchSectionAbsent = PsiTreeUtil.treeWalkUp(element, clazz, new TryBlockCatchTypeProcessor(myType));
        if (!catchSectionAbsent) return true;
      }
      return false;
    }

    @NotNull PsiJavaCodeReferenceElement getReference() {
      return myReference;
    }

    @NotNull PsiClassType getType() {
      return myType;
    }

    private static class TryBlockCatchTypeProcessor implements PairProcessor<PsiElement, PsiElement> {

      private final @NotNull PsiClassType myType;

      private TryBlockCatchTypeProcessor(@NotNull PsiClassType type) {
        myType = type;
      }

      @Override
      public boolean process(PsiElement curr, PsiElement prev) {
        if (!(curr instanceof PsiTryStatement tryStatement)) return true;

        return Arrays.stream(tryStatement.getCatchSections())
          .map(PsiCatchSection::getCatchType)
          .filter(Objects::nonNull)
          .filter(e -> ! ExceptionUtil.isGeneralExceptionType(e))
          .noneMatch(e -> e.isAssignableFrom(myType));
      }
    }
  }

  /**
   * See {@link #checkInconsistency(PsiReferenceList, String)}
   */
  private static void checkInconsistency(@NotNull PsiReferenceList throwsList) {
    checkInconsistency(throwsList, null);
  }

  /**
   * Check the weird inconsistencies when the length of {@link PsiReferenceList#getReferencedTypes} varies from
   * {@link PsiReferenceList#getReferenceElements} in the throws list
   * @param throwsList the throws list element to examine
   * @param methodClassName the class of the method the throws list is from. If the value is null then calculate it with
   * {@link PsiTreeUtil#getParentOfType}
   */
  private static void checkInconsistency(@NotNull PsiReferenceList throwsList, @Nullable String methodClassName) {

    final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();

    final PsiClassType[] referencedTypes = throwsList.getReferencedTypes();

    if (referenceElements.length != referencedTypes.length) {
      if (methodClassName == null) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(throwsList, PsiMethod.class);

        methodClassName = method != null ? method.getClass().getSimpleName() : "null";
      }
      LOGGER.error("Stub-PSI inconsistency detected. " +
                   "The number of elements in the throws list doesn't match the number of types in the throws list. " +
                   "The method's class is " + methodClassName,
                   new Attachment("throwsList.sourcePsi.txt", throwsList.isValid() ? throwsList.getText() : "<invalid>"));
    }
  }
}