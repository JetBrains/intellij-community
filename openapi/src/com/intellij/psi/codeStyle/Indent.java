/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

public interface Indent {
  boolean isGreaterThan(Indent indent);

  Indent min(Indent anotherIndent);
  Indent max(Indent anotherIndent);

  Indent add(Indent indent);
  Indent subtract(Indent indent);

  boolean isZero();
}
