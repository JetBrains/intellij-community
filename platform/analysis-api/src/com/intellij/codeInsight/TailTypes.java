// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import org.jetbrains.annotations.NotNull;

public final class TailTypes {
  private TailTypes() { }

  public static @NotNull TailType unknownType() {
    return TailTypeFactory.getInstance().unknownType();
  }

  public static @NotNull TailType noneType() {
    return TailTypeFactory.getInstance().noneType();
  }

  public static @NotNull TailType semicolonType() {
    return TailTypeFactory.getInstance().semicolonType();
  }

  /**
   * insert a space, overtype if already present
   */
  public static @NotNull TailType spaceType() {
    return TailTypeFactory.getInstance().spaceType();
  }

  /**
   * always insert a space
   */
  public static @NotNull TailType insertSpaceType() {
    return TailTypeFactory.getInstance().insertSpaceType();
  }

  /**
   * insert a space unless there's one at the caret position already, followed by a word or '@'
   */
  public static @NotNull TailType humbleSpaceBeforeWordType() {
    return TailTypeFactory.getInstance().humbleSpaceBeforeWordType();
  }

  public static @NotNull TailType dotType() {
    return TailTypeFactory.getInstance().dotType();
  }

  public static @NotNull TailType caseColonType() {
    return TailTypeFactory.getInstance().caseColonType();
  }

  public static @NotNull TailType equalsType() {
    return TailTypeFactory.getInstance().equalsType();
  }

  public static @NotNull TailType conditionalExpressionColonType() {
    return TailTypeFactory.getInstance().conditionalExpressionColonType();
  }

  public static @NotNull TailType charType(char aChar) {
    return TailTypeFactory.getInstance().charType(aChar);
  }

  public static @NotNull TailType charType(char aChar, boolean overwrite) {
    return TailTypeFactory.getInstance().charType(aChar, overwrite);
  }
}
