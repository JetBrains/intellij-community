package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaGlobalMemberLookupElement extends LookupElement {
  private final PsiMethod myMethod;
  private final PsiClass myContainingClass;
  private final InsertHandler<JavaGlobalMemberLookupElement> myQualifiedInsertion;
  private final InsertHandler<JavaGlobalMemberLookupElement> myImportInsertion;
  private boolean myShouldImport = false;

  public JavaGlobalMemberLookupElement(PsiMethod method,
                                       PsiClass containingClass,
                                       InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsertion,
                                       InsertHandler<JavaGlobalMemberLookupElement> importInsertion, boolean shouldImport) {
    myMethod = method;
    myContainingClass = containingClass;
    myQualifiedInsertion = qualifiedInsertion;
    myImportInsertion = importInsertion;
    myShouldImport = shouldImport;
  }

  @NotNull
  @Override
  public PsiMethod getObject() {
    return myMethod;
  }

  @NotNull
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myMethod.getName();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    final String className = myContainingClass.getName();

    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));

    final String methodName = myMethod.getName();
    if (Boolean.FALSE.equals(myShouldImport) && StringUtil.isNotEmpty(className)) {
      presentation.setItemText(className + "." + methodName);
    } else {
      presentation.setItemText(methodName);
    }

    final String params = PsiFormatUtil.formatMethod(myMethod, PsiSubstitutor.EMPTY,
                                                     PsiFormatUtil.SHOW_PARAMETERS,
                                                     PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
    if (Boolean.TRUE.equals(myShouldImport) && StringUtil.isNotEmpty(className)) {
      presentation.setTailText(params + " in " + className);
    } else {
      presentation.setTailText(params);
    }

    final PsiType type = myMethod.getReturnType();
    if (type != null) {
      presentation.setTypeText(type.getPresentableText());
    }
  }

  public boolean isShouldImport() {
    return myShouldImport;
  }

  public void setShouldImport(boolean shouldImport) {
    myShouldImport = shouldImport;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    (myShouldImport ? myImportInsertion : myQualifiedInsertion).handleInsert(context, this);
  }
}
