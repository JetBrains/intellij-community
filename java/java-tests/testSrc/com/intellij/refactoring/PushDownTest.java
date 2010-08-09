/*
 * User: anna
 * Date: 13-Mar-2008
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.*;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.JavaTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.util.ArrayList;
import java.util.List;

public class PushDownTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean failure) throws Exception {
    final String filePath = "/refactoring/pushDown/" + getTestName(false)+ ".java";
    configureByFile(filePath);

    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on member name", targetElement instanceof PsiMember);

    final PsiMember psiMember = (PsiMember)targetElement;

    final PsiClass currentClass = psiMember.getContainingClass();

    assert currentClass != null;

    final List<MemberInfo> membersToMove = new ArrayList<MemberInfo>();

    final PsiField fieldByName = currentClass.findFieldByName("fieldToMove", false);
    if (fieldByName != null) {
      final MemberInfo memberInfo = new MemberInfo(fieldByName);
      memberInfo.setChecked(true);
      membersToMove.add(memberInfo);
    }

    final MemberInfo memberInfo = new MemberInfo(psiMember);
    memberInfo.setChecked(true);
    membersToMove.add(memberInfo);

    new PushDownProcessor(getProject(), membersToMove.toArray(new MemberInfo[membersToMove.size()]), currentClass, new DocCommentPolicy(DocCommentPolicy.ASIS)){
      @Override
      protected boolean showConflicts(MultiMap<PsiElement, String> conflicts, UsageInfo[] usages) {
        if (failure ? conflicts.isEmpty() : !conflicts.isEmpty()) {
          fail(failure ? "Conflict was not detected" : "False conflict was detected");
        }
        return true;
      }
    }.run();

    checkResultByFile(filePath + ".after");
  }

  public void testTypeParameter() throws Exception {
    doTest();
  }

  public void testTypeParameterErasure() throws Exception {
    doTest();
  }

  public void testFieldTypeParameter() throws Exception {
    doTest();
  }

  public void testBodyTypeParameter() throws Exception {
    doTest();
  }

  public void testDisagreeTypeParameter() throws Exception {
    doTest(true);
  }

  public void testFieldAndReferencedClass() throws Exception {
    doTest();
  }

  public void testFieldAndStaticReferencedClass() throws Exception {
    doTest();
  }

  public void testThisRefInAnonymous() throws Exception {
    doTest();
  }

  public void testSuperOverHierarchyConflict() throws Exception {
    doTest(true);
  }

  public void testSuperOverHierarchy() throws Exception {
    doTest();
  }

  public void testMethodTypeParametersList() throws Exception {
    doTest();
  }
}
