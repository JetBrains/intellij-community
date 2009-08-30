package com.intellij.refactoring.extractSuperclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.ClassMembersUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author dsl
 */
public class ExtractSuperClassViewDescriptor extends UsageViewDescriptorAdapter {
  final PsiElement[] myElements;
  final List<PsiElement> myMembersToMakeWritable = new ArrayList<PsiElement>();

  public ExtractSuperClassViewDescriptor(
    PsiDirectory targetDirectory,
    PsiClass subclass,
    MemberInfo[] infos
  ) {
    super();
    myElements = new PsiElement[infos.length + 2];
    myElements[0] = subclass;
    myElements[1] = targetDirectory;
    myMembersToMakeWritable.add(subclass);
    for (int i = 0; i < infos.length; i++) {
      final MemberInfo info = infos[i];
      myElements[i + 2] = info.getMember();
      if (ClassMembersUtil.isProperMember(info)) {
        myMembersToMakeWritable.add(info.getMember());
      }
    }
  }

  @NotNull
  public PsiElement[] getElements() {
    return myElements;
  }

  public List<PsiElement> getMembersToMakeWritable() {
    return myMembersToMakeWritable;
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("extract.superclass.elements.header");
  }
}
