package com.intellij.refactoring.move.moveMembers;

import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;

import java.util.Collection;

/**
 * @author dyoma
 */
public class MockMoveMembersOptions implements MoveMembersOptions {
  private final PsiMember[] mySelectedMembers;
  private final String myTargetClassName;
  private String myMemberVisibility = PsiModifier.PUBLIC;

  public MockMoveMembersOptions(String targetClassName, PsiMember[] selectedMembers) {
    mySelectedMembers = selectedMembers;
    myTargetClassName = targetClassName;
  }

  public MockMoveMembersOptions(String targetClassName, Collection<PsiMember> memberSet) {
    this(targetClassName, memberSet.toArray(new PsiMember[memberSet.size()]));
  }

  public String getMemberVisibility() {
    return myMemberVisibility;
  }

  public boolean makeEnumConstant() {
    return true;
  }

  public void setMemberVisibility(String visibility) {
    myMemberVisibility = visibility;
  }

  public PsiMember[] getSelectedMembers() {
    return mySelectedMembers;
  }

  public String getTargetClassName() {
    return myTargetClassName;
  }

}
