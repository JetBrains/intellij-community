package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author peter
 */
public class JavaGlobalMemberLookupElement extends LookupElement implements StaticallyImportable {
  private final PsiMember myMember;
  private final PsiClass myContainingClass;
  private final InsertHandler<JavaGlobalMemberLookupElement> myQualifiedInsertion;
  private final InsertHandler<JavaGlobalMemberLookupElement> myImportInsertion;
  private boolean myShouldImport = false;

  public JavaGlobalMemberLookupElement(PsiMember member,
                                       PsiClass containingClass,
                                       InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsertion,
                                       InsertHandler<JavaGlobalMemberLookupElement> importInsertion, boolean shouldImport) {
    myMember = member;
    myContainingClass = containingClass;
    myQualifiedInsertion = qualifiedInsertion;
    myImportInsertion = importInsertion;
    myShouldImport = shouldImport;
  }

  @NotNull
  @Override
  public PsiMember getObject() {
    return myMember;
  }

  @NotNull
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return assertNotNull(myMember.getName());
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    final String className = myContainingClass.getName();

    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));

    final String methodName = myMember.getName();
    if (Boolean.FALSE.equals(myShouldImport) && StringUtil.isNotEmpty(className)) {
      presentation.setItemText(className + "." + methodName);
    } else {
      presentation.setItemText(methodName);
    }

    final String params = myMember instanceof PsiMethod
                          ? PsiFormatUtil.formatMethod((PsiMethod)myMember, PsiSubstitutor.EMPTY,
                                                     PsiFormatUtil.SHOW_PARAMETERS,
                                                     PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE)
                          : "";
    if (Boolean.TRUE.equals(myShouldImport) && StringUtil.isNotEmpty(className)) {
      presentation.setTailText(params + " in " + className);
    } else {
      presentation.setTailText(params);
    }

    final PsiType type = myMember instanceof PsiMethod ? ((PsiMethod)myMember).getReturnType() : ((PsiField) myMember).getType();
    if (type != null) {
      presentation.setTypeText(type.getPresentableText());
    }
  }

  @Override
  public void setShouldBeImported(boolean shouldImportStatic) {
    myShouldImport = shouldImportStatic;
  }

  @Override
  public boolean canBeImported() {
    return true;
  }

  @Override
  public boolean willBeImported() {
    return myShouldImport;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    (myShouldImport ? myImportInsertion : myQualifiedInsertion).handleInsert(context, this);
  }
}
