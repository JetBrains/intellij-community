/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 20, 2002
 * Time: 8:49:24 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;

public class ImportClassFix extends ImportClassFixBase<PsiJavaCodeReferenceElement> {

  public ImportClassFix(@NotNull PsiJavaCodeReferenceElement element) {
    super(element);
  }

  @Override
  protected String getReferenceName(PsiJavaCodeReferenceElement reference) {
    return reference.getReferenceName();
  }

  @Override
  protected void bindReference(PsiJavaCodeReferenceElement ref, PsiClass targetClass) {
    if (ref instanceof PsiImportStaticReferenceElement) {
      ((PsiImportStaticReferenceElement)ref).bindToTargetClass(targetClass);
    }
    else {
      super.bindReference(ref, targetClass);
    }
  }

  @Override
  protected boolean hasTypeParameters(PsiJavaCodeReferenceElement reference) {
    final PsiReferenceParameterList refParameters = reference.getParameterList();
    return refParameters != null && refParameters.getTypeParameterElements().length > 0;
  }

  @Override
  protected String getQualifiedName(PsiJavaCodeReferenceElement reference) {
    return reference.getQualifiedName();
  }

  @Override
  protected boolean isQualified(PsiJavaCodeReferenceElement reference) {
    return reference.isQualified();
  }

  @Override
  protected boolean hasUnresolvedImportWhichCanImport(final PsiFile psiFile, final String name) {
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
      // rough heuristic, since there is no API to get class name refrence from static import
      if (qualifiedName != null && StringUtil.split(qualifiedName, ".").contains(name)) return true;
    }
    return false;
  }

  protected boolean isAccessible(PsiClass aClass, PsiJavaCodeReferenceElement reference) {
    return aClass.hasModifierProperty(PsiModifier.PUBLIC);
  }
}
