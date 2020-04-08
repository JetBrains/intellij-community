/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.extractSuperclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.classMembers.ClassMembersUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ExtractSuperClassViewDescriptor extends UsageViewDescriptorAdapter {
  final PsiElement[] myElements;
  final List<PsiElement> myMembersToMakeWritable = new ArrayList<>();

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

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElements;
  }

  public List<PsiElement> getMembersToMakeWritable() {
    return myMembersToMakeWritable;
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("extract.superclass.elements.header");
  }
}
