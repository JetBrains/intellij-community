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
package com.intellij.refactoring.ui;

import com.intellij.psi.PsiModifier;
import com.intellij.util.VisibilityUtil;

/**
 * @author Konstantin Bulenkov
 */
public class JavaComboBoxVisibilityPanel extends ComboBoxVisibilityPanel<String> {
  private static final String[] MODIFIERS = {PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC};

  private static final String[] PRESENTABLE_NAMES = {
    VisibilityUtil.toPresentableText(PsiModifier.PRIVATE),
    VisibilityUtil.toPresentableText(PsiModifier.PACKAGE_LOCAL),
    VisibilityUtil.toPresentableText(PsiModifier.PROTECTED),
    VisibilityUtil.toPresentableText(PsiModifier.PUBLIC)
  };

  public JavaComboBoxVisibilityPanel() {
    super(MODIFIERS, PRESENTABLE_NAMES);
  }
}
