package com.intellij.refactoring;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiTypeParameterListOwner;

import java.util.List;

/**
 * @author ven
 */
public interface MakeStaticRefactoring<T extends PsiTypeParameterListOwner> extends Refactoring {
  T getMember();

  boolean isReplaceUsages();

  String getClassParameterName();

  List<PsiField> getFields();

  String getParameterNameForField(PsiField field);
}
