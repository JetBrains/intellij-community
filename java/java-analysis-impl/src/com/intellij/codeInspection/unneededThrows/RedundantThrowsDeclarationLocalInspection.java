// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.JavaOverridingMethodUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.psi.PsiModifier.ABSTRACT;

@SuppressWarnings("InspectionDescriptionNotFoundInspection") // delegates
public final class RedundantThrowsDeclarationLocalInspection extends AbstractBaseJavaLocalInspectionTool {
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
   * The method extracts throws declarations that are probably redundant from both the method's throws list and javadoc.
   * It filters out throws declarations that are either of java.lang.RuntimeException or java.rmi.RemoteException type.
   *
   * @param method            method to extract throws declarations from
   * @param ignoreEntryPoints whether or not to extract throws if the method is an entry point (e.g. public static void main)
   * @return a stream of throws declarations that are candidates for redundant declarations
   */
  @Contract(pure = true)
  static StreamEx<ThrowRefType> getRedundantThrowsCandidates(@Nullable final PsiMethod method, final boolean ignoreEntryPoints) {
    if (method == null) return StreamEx.empty();
    if (ignoreEntryPoints && UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(method)) return StreamEx.empty();
    if (method.hasModifier(JvmModifier.NATIVE)) return StreamEx.empty();
    if (JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass())) return StreamEx.empty();

    final PsiReferenceList throwsList = method.getThrowsList();
    final StreamEx<ThrowRefType> redundantInThrowsList = StreamEx.zip(throwsList.getReferenceElements(),
                                                                      throwsList.getReferencedTypes(),
                                                                      ThrowRefType::new);

    final PsiDocComment comment = method.getDocComment();
    final Stream<ThrowRefType> redundantInJavadoc;
    if (comment != null) {
      redundantInJavadoc = Arrays.stream(comment.getTags())
        .filter(tag -> "throws".equals(tag.getName()))
        .flatMap(tag -> {
          final PsiClass throwsClass = JavaDocUtil.resolveClassInTagValue(tag.getValueElement());
          if (throwsClass == null) return Stream.empty();
          return Stream.of(new ThrowRefType(tag, TypeUtils.getType(throwsClass)));
        });
    }
    else {
      redundantInJavadoc = Stream.empty();
    }

    return redundantInThrowsList.append(redundantInJavadoc)
      .filter(ThrowRefType::isCheckedException)
      .filter(p -> !p.isRemoteExceptionInRemoteMethod(method));
  }

  private static final class RedundantThrowsVisitor extends JavaElementVisitor {

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
        .filter(throwRefType -> !throwRefType.isInOverridenOf(method))
        .forEach(throwRefType -> {
          final PsiElement reference = throwRefType.myReference;
          final PsiClassType exceptionType = throwRefType.myType;

          final String description = JavaErrorBundle.message("exception.is.never.thrown", JavaHighlightUtil.formatType(exceptionType));
          final RedundantThrowsQuickFix fix = new RedundantThrowsQuickFix(exceptionType.getCanonicalText(), method.getName());
          myHolder.registerProblem(reference, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
        });
    }

    private static final class RedundantThrowsQuickFix implements LocalQuickFix {

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
      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiElement elem = descriptor.getPsiElement();

        final CommentTracker ct = new CommentTracker();
        ct.deleteAndRestoreComments(elem);
      }

    }
  }

  /**
   * Holder for a throw declaration (either in throws list or javadoc) in a method and its exception class type.
   */
  static final class ThrowRefType {
    @NotNull private final PsiElement myReference;
    @NotNull private final PsiClassType myType;

    private ThrowRefType(@NotNull final PsiElement reference, @NotNull final PsiClassType type) {
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
    private boolean isInOverridenOf(@NotNull final PsiMethod method) {
      if (!isMethodPossiblyOverriden(method)) return false;

      final Predicate<PsiMethod> methodContainsThrownExceptions = m -> !ArrayUtil.isEmpty(m.getThrowsList().getReferencedTypes());

      final Stream<PsiMethod> overridingMethods = JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method,
                                                                                                             null,
                                                                                                             methodContainsThrownExceptions);
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

      return unhandled.stream().anyMatch(myType::isAssignableFrom);
    }

    @Contract(pure = true)
    private boolean isInThrowsListOf(@NotNull final PsiMethod method) {
      return Arrays.stream(method.getThrowsList().getReferencedTypes())
        .anyMatch(myType::isAssignableFrom);
    }

    @Contract(pure = true)
    private static boolean isMethodPossiblyOverriden(@NotNull final PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return false;

      final PsiModifierList modifierList = method.getModifierList();

      return !(modifierList.hasModifierProperty(PsiModifier.PRIVATE) ||
               modifierList.hasModifierProperty(PsiModifier.STATIC) ||
               modifierList.hasModifierProperty(PsiModifier.FINAL) ||
               method.isConstructor() ||
               containingClass instanceof PsiAnonymousClass ||
               containingClass.hasModifierProperty(PsiModifier.FINAL));
    }

    @NotNull PsiElement getReference() {
      return myReference;
    }

    @NotNull PsiClassType getType() {
      return myType;
    }
  }
}
