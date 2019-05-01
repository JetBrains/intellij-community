package com.intellij.dupLocator.equivalence;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class MultiChildDescriptor {
  private final MyType myType;
  private final PsiElement[] myElements;

  public MultiChildDescriptor(@NotNull MyType type, @NotNull PsiElement[] elements) {
    myType = type;
    myElements = elements;
  }

  @NotNull
  public MyType getType() {
    return myType;
  }

  @NotNull
  public PsiElement[] getElements() {
    return myElements;
  }

  public enum MyType {
    DEFAULT,
    OPTIONALLY,
    OPTIONALLY_IN_PATTERN,
    IN_ANY_ORDER
  }
}
