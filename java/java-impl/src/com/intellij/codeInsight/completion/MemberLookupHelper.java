package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class MemberLookupHelper {
  private final PsiMember myMember;
  private final boolean myMergedOverloads;
  @Nullable private final PsiClass myContainingClass;
  private boolean myShouldImport = false;

  public MemberLookupHelper(List<PsiMethod> overloads, PsiClass containingClass, boolean shouldImport) {
    this(overloads.get(0), containingClass, shouldImport, true);
  }

  public MemberLookupHelper(PsiMember member, PsiClass containingClass, boolean shouldImport, final boolean mergedOverloads) {
    myMember = member;
    myContainingClass = containingClass;
    myShouldImport = shouldImport;
    myMergedOverloads = mergedOverloads;
  }

  public PsiMember getMember() {
    return myMember;
  }

  @Nullable
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public void setShouldBeImported(boolean shouldImportStatic) {
    myShouldImport = shouldImportStatic;
  }

  public boolean willBeImported() {
    return myShouldImport;
  }

  public void renderElement(LookupElementPresentation presentation, @Nullable Boolean qualify, PsiSubstitutor substitutor) {
    final String className = myContainingClass == null ? "???" : myContainingClass.getName();

    final String memberName = myMember.getName();
    if (!Boolean.FALSE.equals(qualify) && (!myShouldImport && StringUtil.isNotEmpty(className) || Boolean.TRUE.equals(qualify))) {
      presentation.setItemText(className + "." + memberName);
    } else {
      presentation.setItemText(memberName);
    }

    final String qname = myContainingClass == null ? "" : myContainingClass.getQualifiedName();
    String pkg = StringUtil.getPackageName(qname);
    String location = StringUtil.isEmpty(pkg) ? "" : " (" + pkg + ")";

    final String params = myMergedOverloads
                          ? "(...)"
                          : myMember instanceof PsiMethod
                            ? PsiFormatUtil.formatMethod((PsiMethod)myMember, PsiSubstitutor.EMPTY,
                                                         PsiFormatUtil.SHOW_PARAMETERS,
                                                         PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE)
                            : "";
    if (myShouldImport && StringUtil.isNotEmpty(className)) {
      presentation.setTailText(params + " in " + className + location);
    } else {
      presentation.setTailText(params + location, !(myMember instanceof PsiMethod));
    }

    final PsiType type = myMember instanceof PsiMethod ? ((PsiMethod)myMember).getReturnType() : ((PsiField) myMember).getType();
    if (type != null) {
      presentation.setTypeText(substitutor.substitute(type).getPresentableText());
    }
  }


}
