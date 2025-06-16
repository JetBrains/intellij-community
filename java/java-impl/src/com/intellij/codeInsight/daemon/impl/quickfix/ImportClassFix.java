// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ImportClassFix extends ImportClassFixBase<PsiJavaCodeReferenceElement, PsiJavaCodeReferenceElement> {
  public ImportClassFix(@NotNull PsiJavaCodeReferenceElement element) {
    super(element, element);
  }

  @Override
  protected String getReferenceName(@NotNull PsiJavaCodeReferenceElement reference) {
    return reference.getReferenceName();
  }

  @Override
  protected PsiElement getReferenceNameElement(@NotNull PsiJavaCodeReferenceElement reference) {
    return reference.getReferenceNameElement();
  }

  @Override
  protected void bindReference(@NotNull PsiReference ref, @NotNull PsiClass targetClass) {
    if (ref instanceof PsiImportStaticReferenceElement) {
      ((PsiImportStaticReferenceElement)ref).bindToTargetClass(targetClass);
    }
    else {
      super.bindReference(ref, targetClass);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    List<? extends PsiClass> classesToImport = getClassesToImport(true);
    if (classesToImport.isEmpty()) return IntentionPreviewInfo.EMPTY;
    PsiClass firstClassToImport = classesToImport.get(0);
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.findSameElementInCopy(getReference(), psiFile);
    bindReference(ref, firstClassToImport);
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  protected boolean hasTypeParameters(@NotNull PsiJavaCodeReferenceElement reference) {
    PsiReferenceParameterList refParameters = reference.getParameterList();
    return refParameters != null && refParameters.getTypeParameterElements().length > 0;
  }

  @Override
  protected String getQualifiedName(@NotNull PsiJavaCodeReferenceElement referenceElement) {
    return referenceElement.getQualifiedName();
  }

  @Override
  protected boolean isQualified(@NotNull PsiJavaCodeReferenceElement reference) {
    return reference.isQualified();
  }

  @Override
  protected boolean hasUnresolvedImportWhichCanImport(@NotNull PsiFile psiFile, @NotNull String name) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    PsiImportList importList = ((PsiJavaFile)psiFile).getImportList();
    if (importList == null) return false;
    PsiImportStatement[] importStatements = importList.getImportStatements();
    for (PsiImportStatement importStatement : importStatements) {
      if (importStatement.resolve() != null) continue;
      if (importStatement.isOnDemand()) return true;
      String qualifiedName = importStatement.getQualifiedName();
      String className = qualifiedName == null ? null : ClassUtil.extractClassName(qualifiedName);
      if (Comparing.strEqual(className, name)) return true;
    }
    PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
      if (importStaticStatement.resolve() != null) continue;
      if (importStaticStatement.isOnDemand()) return true;
      String qualifiedName = importStaticStatement.getReferenceName();
      // rough heuristic, since there is no API to get class name reference from static import
      if (qualifiedName != null && StringUtil.split(qualifiedName, ".").contains(name)) return true;
    }
    return false;
  }

  @Override
  protected String getRequiredMemberName(@NotNull PsiJavaCodeReferenceElement referenceElement) {
    PsiElement parent = referenceElement.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement) {
      return ((PsiJavaCodeReferenceElement)parent).getReferenceName();
    }

    return super.getRequiredMemberName(referenceElement);
  }

  @Override
  protected boolean canReferenceClass(@NotNull PsiJavaCodeReferenceElement ref) {
    if (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null) return false;
    if (ref instanceof PsiReferenceExpression) {
      PsiElement parent = ref.getParent();
      return parent instanceof PsiReferenceExpression || parent instanceof PsiExpressionStatement;
    }
    return !inReturnTypeOfIncompleteGenericMethod(ref);
  }

  private static boolean inReturnTypeOfIncompleteGenericMethod(@NotNull PsiJavaCodeReferenceElement element) {
    PsiTypeElement type = SyntaxTraverser.psiApi().parents(element).filter(PsiTypeElement.class).last();
    PsiElement prev = FilterPositionUtil.searchNonSpaceNonCommentBack(type);
    PsiTypeParameterList typeParameterList = PsiTreeUtil.getParentOfType(prev, PsiTypeParameterList.class);
    if (typeParameterList != null && typeParameterList.getParent() instanceof PsiErrorElement) {
      return ContainerUtil.exists(typeParameterList.getTypeParameters(), p -> Objects.equals(element.getReferenceName(), p.getName()));
    }
    return false;
  }

  @Override
  protected @Unmodifiable @NotNull Collection<PsiClass> filterByContext(@NotNull Collection<PsiClass> candidates, @NotNull PsiJavaCodeReferenceElement referenceElement) {
    if (referenceElement instanceof PsiReferenceExpression) {
      return Collections.emptyList();
    }

    PsiElement typeElement = referenceElement.getParent();
    if (typeElement instanceof PsiTypeElement) {
      PsiElement var = typeElement.getParent();
      if (var instanceof PsiVariable) {
        PsiExpression initializer = ((PsiVariable)var).getInitializer();
        if (initializer != null) {
          PsiType type = initializer.getType();
          if (type != null) {
            return filterAssignableFrom(type, candidates);
          }
        }
      }
      if (var instanceof PsiParameter) {
        return filterBySuperMethods((PsiParameter)var, candidates);
      }
    }

    return super.filterByContext(candidates, referenceElement);
  }

  @Override
  protected boolean isAccessible(@NotNull PsiMember member, @NotNull PsiJavaCodeReferenceElement referenceElement) {
    return PsiUtil.isAccessible(member, referenceElement, null);
  }

  @Override
  protected boolean isClassDefinitelyPositivelyImportedAlready(@NotNull PsiFile containingFile, @NotNull PsiClass classToImport) {
    if (containingFile instanceof PsiJavaFile) {
      PsiImportList importList = ((PsiJavaFile)containingFile).getImportList();
      if (importList == null) return false;
      boolean result = false;
      String classQualifiedName = classToImport.getQualifiedName();
      String packageName = classQualifiedName == null ? "" : StringUtil.getPackageName(classQualifiedName);
      for (PsiImportStatementBase statement : importList.getAllImportStatements()) {
        PsiJavaCodeReferenceElement importRef = statement.getImportReference();
        if (importRef == null) continue;
        String canonicalText = importRef.getCanonicalText(); // rely on the optimization: no resolve while getting import statement canonical text

        if (statement.isOnDemand()) {
          if (canonicalText.equals(packageName)) {
            result = true;
            break;
          }
        }
        else {
          if (canonicalText.equals(classQualifiedName)) {
            result = true;
            break;
          }
        }
      }
      return result;
    }
    if (containingFile instanceof JavaCodeFragment) {
      String classQualifiedName = classToImport.getQualifiedName();
      return classQualifiedName != null && ((JavaCodeFragment)containingFile).importsToString().contains(classQualifiedName);
    }
    return false;
  }

  @Override
  public String toString() {
    return "ImportClassFix: "+getReference()+" -> "+getClassesToImport();
  }
}
