// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PossibleHeapPollutionVarargsInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(PossibleHeapPollutionVarargsInspection.class);
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "SafeVarargsDetector";
  }

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "unchecked";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new HeapPollutionVisitor(holder);
  }

  private static class AnnotateAsSafeVarargsQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("annotate.as.safevarargs");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (!(psiElement instanceof PsiIdentifier)) return;
      PsiModifierListOwner owner = (PsiModifierListOwner)psiElement.getParent();
      if (owner instanceof PsiClass) {
        PsiClass rec = (PsiClass)owner;
        if (!rec.isRecord()) return;
        String compactCtorText = "public " + rec.getName() + " {}";
        PsiMethod ctor = JavaPsiFacade.getElementFactory(project).createMethodFromText(compactCtorText, owner);
        PsiMethod firstMethod = ArrayUtil.getFirstElement(rec.getMethods());
        owner = (PsiMethod)WriteCommandAction.writeCommandAction(owner.getContainingFile()).withName(getFamilyName())
          .compute(() -> rec.addBefore(ctor, firstMethod));
      }
      if (owner instanceof PsiMethod) {
        new AddAnnotationPsiFix(CommonClassNames.JAVA_LANG_SAFE_VARARGS, owner, PsiNameValuePair.EMPTY_ARRAY).applyFix(project, descriptor);
      }
    }
  }

  private static class MakeFinalAndAnnotateQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("make.final.and.annotate.as.safevarargs");
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return currentFile;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiIdentifier) {
        final PsiMethod psiMethod = (PsiMethod)psiElement.getParent();
        WriteAction.run(() -> psiMethod.getModifierList().setModifierProperty(PsiModifier.FINAL, true));
        new AddAnnotationPsiFix(CommonClassNames.JAVA_LANG_SAFE_VARARGS, psiMethod, PsiNameValuePair.EMPTY_ARRAY).applyFix(project, descriptor);
      }
    }
  }

  public static class HeapPollutionVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public HeapPollutionVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!PsiUtil.getLanguageLevel(method).isAtLeast(LanguageLevel.JDK_1_7)) return;
      if (AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_SAFE_VARARGS, 0)) return;
      if (!method.isVarArgs()) return;

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiParameter psiParameter = parameters[parameters.length - 1];
      if (!psiParameter.isVarArgs()) return;

      checkForHeapPollution(method, psiParameter);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      if (!aClass.isRecord()) return;
      if (AnnotationUtil.isAnnotated(aClass, CommonClassNames.JAVA_LANG_SAFE_VARARGS, 0)) return;
      PsiRecordHeader header = aClass.getRecordHeader();
      if (header == null) return;
      PsiRecordComponent lastComponent = ArrayUtil.getLastElement(header.getRecordComponents());
      if (lastComponent == null || !lastComponent.isVarArgs()) return;
      PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
      if (constructor != null && constructor.isPhysical()) return; // will be reported on constructor instead
      final PsiType type = lastComponent.getType();
      LOG.assertTrue(type instanceof PsiEllipsisType, "type: " + type.getCanonicalText() + "; param: " + lastComponent);

      final PsiType componentType = ((PsiEllipsisType)type).getComponentType();
      if (JavaGenericsUtil.isReifiableType(componentType)) {
        return;
      }
      final PsiElement nameIdentifier = ((PsiNameIdentifierOwner)aClass).getNameIdentifier();
      if (nameIdentifier != null) {
        final LocalQuickFix quickFix = new AnnotateAsSafeVarargsQuickFix();
        myHolder.registerProblem(nameIdentifier, JavaAnalysisBundle.message("possible.heap.pollution.from.parameterized.vararg.type.loc"), quickFix);
      }
    }

    private void checkForHeapPollution(PsiMethod method, PsiVariable psiParameter) {
      final PsiType type = psiParameter.getType();
      LOG.assertTrue(type instanceof PsiEllipsisType, "type: " + type.getCanonicalText() + "; param: " + psiParameter);

      final PsiType componentType = ((PsiEllipsisType)type).getComponentType();
      if (JavaGenericsUtil.isReifiableType(componentType)) {
        return;
      }
      for (PsiReference reference : ReferencesSearch.search(psiParameter)) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiExpression && !PsiUtil.isAccessedForReading((PsiExpression)element)) {
          return;
        }
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) {
        //if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
        //final PsiClass containingClass = method.getContainingClass();
        //if (containingClass == null || containingClass.isInterface()) return; do not add
        registerProblem(method, nameIdentifier);
      }
    }

    protected void registerProblem(PsiMethod method, PsiIdentifier nameIdentifier) {
      final LocalQuickFix quickFix;
      if (GenericsHighlightUtil.isSafeVarargsNoOverridingCondition(method, PsiUtil.getLanguageLevel(method))) {
        quickFix = new AnnotateAsSafeVarargsQuickFix();
      }
      else {
        final PsiClass containingClass = method.getContainingClass();
        LOG.assertTrue(containingClass != null);
        boolean canBeFinal = !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
                             !containingClass.isInterface() &&
                             OverridingMethodsSearch.search(method).findFirst() == null;
        quickFix = canBeFinal ? new MakeFinalAndAnnotateQuickFix() : null;
      }
      myHolder.registerProblem(nameIdentifier, JavaAnalysisBundle.message("possible.heap.pollution.from.parameterized.vararg.type.loc"), quickFix);
    }
  }
}
