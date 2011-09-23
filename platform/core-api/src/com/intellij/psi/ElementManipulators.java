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
package com.intellij.psi;

import com.intellij.openapi.util.ClassExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ElementManipulators extends ClassExtension<ElementManipulator> {

  @NonNls public static final String EP_NAME = "com.intellij.lang.elementManipulator";
  public static final ElementManipulators INSTANCE = new ElementManipulators();


  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ElementManipulators");

  private ElementManipulators() {
    super(EP_NAME);
  }

  public static <T extends PsiElement> ElementManipulator<T> getManipulator(@NotNull T element) {
    return ElementManipulators.INSTANCE.forClass(element.getClass());
  }

  public static int getOffsetInElement(final PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = getManipulator(element);
    LOG.assertTrue(manipulator != null, element.getClass().getName());
    return manipulator.getRangeInElement(element).getStartOffset();
  }

  public static TextRange getValueTextRange(final PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = getManipulator(element);
    assert manipulator != null: element.getClass().getName();
    return manipulator.getRangeInElement(element);
  }

  public static String getValueText(final PsiElement element) {
    final TextRange valueTextRange = getValueTextRange(element);
    if (valueTextRange.isEmpty()) return "";

    final String text = element.getText();
    LOG.assertTrue(valueTextRange.getEndOffset() <= text.length(), "Wrong range for " + element + " text: " + text + " range " + valueTextRange);
    return valueTextRange.substring(text);
  }
}
