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
package com.intellij.refactoring.changeClassSignature;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ChangeClassSigntaureViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiClass myClass;

  public ChangeClassSigntaureViewDescriptor(PsiClass aClass) {
    super();
    myClass = aClass;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[]{myClass};
  }

  @Override
  public String getProcessedElementsHeader() {
    return StringUtil.capitalize(UsageViewUtil.getType(myClass));
  }
}
