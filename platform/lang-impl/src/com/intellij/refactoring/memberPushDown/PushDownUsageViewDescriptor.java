// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PushDownUsageViewDescriptor<MemberInfo extends MemberInfoBase<Member>,
                                        Member extends PsiElement,
                                        Klass extends PsiElement> implements UsageViewDescriptor {
  private final PsiElement[] myMembers;
  private final @NlsContexts.ListItem String myProcessedElementsHeader;

  public PushDownUsageViewDescriptor(Klass aClass) {
    this(aClass, null);
  }

  public PushDownUsageViewDescriptor(Klass aClass, List<? extends MemberInfo> memberInfos) {
    myMembers = memberInfos != null ? ContainerUtil.map2Array(memberInfos, PsiElement.class, MemberInfoBase::getMember) : new PsiElement[]{aClass};
    myProcessedElementsHeader = RefactoringBundle.message("push.down.members.elements.header",
                                                          memberInfos != null ? DescriptiveNameUtil.getDescriptiveName(aClass) : "");
  }

  @Override
  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myMembers;
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
