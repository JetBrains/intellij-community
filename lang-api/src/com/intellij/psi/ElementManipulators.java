/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.ClassExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ElementManipulators extends ClassExtension<ElementManipulator> {
  @NonNls public static final String EP_NAME = "com.intellij.lang.elementManipulator";
  public static final ElementManipulators INSTANCE = new ElementManipulators();

  private ElementManipulators() {
    super(EP_NAME);
  }

  @Nullable
  public static <T extends PsiElement> ElementManipulator<T> getManipulator(@NotNull T element) {
    return ElementManipulators.INSTANCE.forClass(element.getClass());
  }

  public static int getOffsetInElement(final PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = getManipulator(element);
    assert manipulator != null: element.getClass().getName();
    return manipulator.getRangeInElement(element).getStartOffset();
  }

}
