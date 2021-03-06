// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClassExtension;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class ElementManipulators extends ClassExtension<ElementManipulator> {

  @NonNls public static final String EP_NAME = "com.intellij.lang.elementManipulator";
  public static final ElementManipulators INSTANCE = new ElementManipulators();


  private static final Logger LOG = Logger.getInstance(ElementManipulators.class);

  private ElementManipulators() {
    super(EP_NAME);
  }

  /**
   * @see #getNotNullManipulator(PsiElement)
   */
  public static <T extends PsiElement> ElementManipulator<T> getManipulator(@NotNull T element) {
    //noinspection unchecked
    return INSTANCE.forClass(element.getClass());
  }

  public static int getOffsetInElement(@NotNull PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = getNotNullManipulator(element);
    return getManipulatorRange(manipulator, element).getStartOffset();
  }

  @NotNull
  public static <T extends PsiElement> ElementManipulator<T> getNotNullManipulator(@NotNull T element) {
    final ElementManipulator<T> manipulator = getManipulator(element);
    LOG.assertTrue(manipulator != null, element.getClass().getName());
    return manipulator;
  }

  @NotNull
  public static TextRange getValueTextRange(@NotNull PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = getManipulator(element);
    return manipulator == null ? TextRange.from(0, element.getTextLength()) : getManipulatorRange(manipulator, element);
  }

  @NotNull
  public static String getValueText(@NotNull PsiElement element) {
    final TextRange valueTextRange = getValueTextRange(element);
    if (valueTextRange.isEmpty()) return "";

    final String text = element.getText();
    if (valueTextRange.getEndOffset() > text.length()) {
      LOG.error("Wrong range for " + element + " text: " + text + " range " + valueTextRange);
    }

    return valueTextRange.substring(text);
  }

  public static <T extends PsiElement> T handleContentChange(@NotNull T element, String text) {
    final ElementManipulator<T> manipulator = getNotNullManipulator(element);
    return manipulator.handleContentChange(element, text);
  }

  public static <T extends PsiElement> T handleContentChange(@NotNull T element, @NotNull TextRange range, String text) {
    final ElementManipulator<T> manipulator = getNotNullManipulator(element);
    return manipulator.handleContentChange(element, range, text);
  }

  @NotNull
  private static TextRange getManipulatorRange(@NotNull ElementManipulator<? super PsiElement> manipulator, @NotNull PsiElement element) {
    TextRange rangeInElement = manipulator.getRangeInElement(element);
    TextRange elementRange = TextRange.from(0, element.getTextLength());
    if (!elementRange.contains(rangeInElement)) {
      LOG.error("Element range: " + elementRange + ";\n" +
                "manipulator range: " + rangeInElement + ";\n" +
                "element: " + element.getClass() + ";\n" +
                "manipulator: " + manipulator.getClass() + ".");
    }
    return rangeInElement;
  }
}
