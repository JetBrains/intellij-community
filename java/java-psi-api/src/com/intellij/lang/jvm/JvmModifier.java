// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

public enum JvmModifier {
  PUBLIC,
  PROTECTED,
  PRIVATE,
  PACKAGE_LOCAL,

  STATIC,
  ABSTRACT,
  FINAL,

  NATIVE,
  SYNCHRONIZED,
  STRICTFP,
  TRANSIENT,
  VOLATILE,
  TRANSITIVE;

  public static final JvmModifier[] EMPTY_ARRAY = new JvmModifier[0];
}
