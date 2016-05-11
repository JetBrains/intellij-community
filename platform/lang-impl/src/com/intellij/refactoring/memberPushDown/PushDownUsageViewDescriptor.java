/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.memberPushDown;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PushDownUsageViewDescriptor<MemberInfo extends MemberInfoBase<Member>,
                                        Member extends PsiElement,
                                        Klass extends PsiElement> implements UsageViewDescriptor {
  private final PsiElement[] myMembers;
  private final String myProcessedElementsHeader;

  public PushDownUsageViewDescriptor(Klass aClass) {
    this(aClass, null);
  }

  public PushDownUsageViewDescriptor(Klass aClass, List<MemberInfo> memberInfos) {
    myMembers = memberInfos != null ? ContainerUtil.map2Array(memberInfos, PsiElement.class, MemberInfoBase::getMember) : new PsiElement[]{aClass};
    myProcessedElementsHeader = RefactoringBundle.message("push.down.members.elements.header", 
                                                          memberInfos != null ? DescriptiveNameUtil.getDescriptiveName(aClass) : "");
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @NotNull
  public PsiElement[] getElements() {
    return myMembers;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
