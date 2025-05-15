/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.ExternalizableStringSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.*;

public final class EmptyClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();
  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean ignoreClassWithParameterization;
  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean ignoreThrowables = true;
  @SuppressWarnings({"PublicField", "WeakerAccess"})
  public boolean commentsAreContent = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                 new JavaClassValidator().annotationsOnly()),
      checkbox("ignoreClassWithParameterization", InspectionGadgetsBundle.message("empty.class.ignore.parameterization.option")),
      checkbox("ignoreThrowables",
               InspectionGadgetsBundle.message("inspection.empty.class.ignore.subclasses.option", CommonClassNames.JAVA_LANG_THROWABLE)),
      checkbox("commentsAreContent", InspectionGadgetsBundle.message("comments.as.content.option"))
    );
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final Object element = infos[0];
    if (element instanceof PsiAnonymousClass) {
      return InspectionGadgetsBundle.message("empty.anonymous.class.problem.descriptor");
    }
    else if (element instanceof PsiClass) {
      return ((PsiClass)element).isEnum() ?
             InspectionGadgetsBundle.message("empty.enum.problem.descriptor"):
             InspectionGadgetsBundle.message("empty.class.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("empty.class.file.without.class.problem.descriptor");
    }
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final Object info = infos[0];
    if (!(info instanceof PsiModifierListOwner owner)) {
      return InspectionGadgetsFix.EMPTY_ARRAY;
    }
    return StreamEx.of(SpecialAnnotationsUtilBase.createAddAnnotationToListFixes(owner, this, insp -> insp.ignorableAnnotations))
      .prepend(info instanceof PsiAnonymousClass ? new ConvertEmptyAnonymousToNewFix() : null)
      .nonNull()
      .toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyClassVisitor();
  }

  private static class ConvertEmptyAnonymousToNewFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      final PsiAnonymousClass aClass;
      if (parent instanceof PsiAnonymousClass) {
        aClass = (PsiAnonymousClass)parent;
      } else if (parent instanceof PsiEnumConstant) {
        aClass = ((PsiEnumConstant)parent).getInitializingClass();
        if (aClass == null) return;
      } else {
        return;
      }
      PsiElement lBrace = aClass.getLBrace();
      PsiElement rBrace = aClass.getRBrace();
      if (lBrace != null && rBrace != null) {
        PsiElement prev = lBrace.getPrevSibling();
        PsiElement start = prev instanceof PsiWhiteSpace ? prev : lBrace;
        Document document = aClass.getContainingFile().getFileDocument();
        int anonymousStart = start.getTextRange().getStartOffset();
        int rBraceEnd = rBrace.getTextRange().getEndOffset();
        document.deleteString(anonymousStart, rBraceEnd);
      }
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("convert.empty.anonymous.to.new.fix.family.name");
    }
  }

  private class EmptyClassVisitor extends BaseInspectionVisitor {
    @Override
    public void visitFile(@NotNull PsiFile psiFile) {
      super.visitFile(psiFile);
      if (!(psiFile instanceof PsiJavaFile javaFile)) {
        return;
      }
      if (javaFile.getClasses().length != 0) {
        return;
      }
      final @NonNls String fileName = javaFile.getName();
      if (PsiPackage.PACKAGE_INFO_FILE.equals(fileName) || PsiJavaModule.MODULE_INFO_FILE.equals(fileName)) {
        return;
      }
      registerError(psiFile, psiFile);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (FileTypeUtils.isInServerPageFile(aClass.getContainingFile())) {
        return;
      }
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isEnum()) {
        for (PsiClass superClass : aClass.getSupers()) {
          if (superClass.isInterface() || superClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
          }
        }
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (PsiTreeUtil.getChildOfType(aClass, PsiMethod.class) != null ||
          PsiTreeUtil.getChildOfType(aClass, PsiField.class) != null) {
        return;
      }
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      if (initializers.length > 0) {
        return;
      }
      if (commentsAreContent && PsiTreeUtil.getChildOfType(aClass, PsiComment.class) != null) {
        return;
      }
      if (ignoreClassWithParameterization && isSuperParametrization(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations, 0)) {
        return;
      }
      if (ignoreThrowables && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      registerClassError(aClass, aClass);
    }

    private static boolean hasTypeArguments(PsiReferenceList extendsList) {
      if (extendsList == null) {
        return false;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList == null) {
          continue;
        }
        final PsiType[] typeArguments = parameterList.getTypeArguments();
        if (typeArguments.length != 0) {
          return true;
        }
      }
      return false;
    }

    private boolean isSuperParametrization(PsiClass aClass) {
      if (!(aClass instanceof PsiAnonymousClass anonymousClass)) {
        final PsiReferenceList extendsList = aClass.getExtendsList();
        final PsiReferenceList implementsList = aClass.getImplementsList();
        return hasTypeArguments(extendsList) || hasTypeArguments(implementsList);
      }
      final PsiJavaCodeReferenceElement reference = anonymousClass.getBaseClassReference();
      final PsiReferenceParameterList parameterList = reference.getParameterList();
      if (parameterList == null) {
        return false;
      }
      final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
      for (PsiTypeElement element : elements) {
        if (element != null) {
          return true;
        }
      }
      return false;
    }
  }
}