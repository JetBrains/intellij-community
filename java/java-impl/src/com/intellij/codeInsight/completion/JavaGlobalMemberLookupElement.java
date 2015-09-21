package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author peter
 */
public class JavaGlobalMemberLookupElement extends LookupElement implements StaticallyImportable {
  private final MemberLookupHelper myHelper;
  private final InsertHandler<JavaGlobalMemberLookupElement> myQualifiedInsertion;
  private final InsertHandler<JavaGlobalMemberLookupElement> myImportInsertion;

  public JavaGlobalMemberLookupElement(List<PsiMethod> overloads,
                                       PsiClass containingClass,
                                       InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsertion,
                                       InsertHandler<JavaGlobalMemberLookupElement> importInsertion, boolean shouldImport) {
    myHelper = new MemberLookupHelper(overloads, containingClass, shouldImport);
    myQualifiedInsertion = qualifiedInsertion;
    myImportInsertion = importInsertion;
  }

  public JavaGlobalMemberLookupElement(PsiMember member,
                                       PsiClass containingClass,
                                       InsertHandler<JavaGlobalMemberLookupElement> qualifiedInsertion,
                                       InsertHandler<JavaGlobalMemberLookupElement> importInsertion, boolean shouldImport) {
    myHelper = new MemberLookupHelper(member, containingClass, shouldImport, false);
    myQualifiedInsertion = qualifiedInsertion;
    myImportInsertion = importInsertion;
  }

  @NotNull
  @Override
  public PsiMember getObject() {
    return myHelper.getMember();
  }

  @NotNull
  public PsiClass getContainingClass() {
    return assertNotNull(myHelper.getContainingClass());
  }

  @NotNull
  @Override
  public String getLookupString() {
    return assertNotNull(getObject().getName());
  }

  @Override
  public Set<String> getAllLookupStrings() {
    return JavaCompletionUtil.getAllLookupStrings(getObject());
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));
    myHelper.renderElement(presentation, !myHelper.willBeImported(), true, PsiSubstitutor.EMPTY);
  }

  @Override
  public void setShouldBeImported(boolean shouldImportStatic) {
    myHelper.setShouldBeImported(shouldImportStatic);
  }

  @Override
  public boolean canBeImported() {
    return true;
  }

  @Override
  public boolean willBeImported() {
    return myHelper.willBeImported();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

    (willBeImported() ? myImportInsertion : myQualifiedInsertion).handleInsert(context, this);
  }

}
