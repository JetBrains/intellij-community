/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import static com.intellij.util.VisibilityUtil.toPresentableText;

/**
 * @author Konstantin Bulenkov
 */
public class JavaComboBoxVisibilityPanel extends ComboBoxVisibilityPanel<String> implements PsiModifier {
  private static final String[] MODIFIERS = {PRIVATE, PACKAGE_LOCAL, PROTECTED, PUBLIC};

  private static final String[] PRESENTABLE_NAMES = {
    toPresentableText(PRIVATE),
    toPresentableText(PACKAGE_LOCAL),
    toPresentableText(PROTECTED),
    toPresentableText(PUBLIC)
  };

  public JavaComboBoxVisibilityPanel() {
    super(MODIFIERS, PRESENTABLE_NAMES);
  }
}
