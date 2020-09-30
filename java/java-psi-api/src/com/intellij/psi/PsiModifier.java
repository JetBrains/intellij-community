// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import org.intellij.lang.annotations.MagicConstant;

/**
 * Provides a list of possible modifier keywords for Java classes, methods and fields.
 */
@NlsSafe
public interface PsiModifier {
  String PUBLIC = "public";
  String PROTECTED = "protected";
  String PRIVATE = "private";
  String PACKAGE_LOCAL = "packageLocal";
  String STATIC = "static";
  String ABSTRACT = "abstract";
  String FINAL = "final";
  String NATIVE = "native";
  String SYNCHRONIZED = "synchronized";
  String STRICTFP = "strictfp";
  String TRANSIENT = "transient";
  String VOLATILE = "volatile";
  String DEFAULT = "default";
  String OPEN = "open";
  String TRANSITIVE = "transitive";
  String SEALED = "sealed";
  String NON_SEALED = "non-sealed";

  String[] MODIFIERS = {
    PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, DEFAULT, OPEN, TRANSITIVE, SEALED, NON_SEALED
  };

  @MagicConstant(stringValues = {
    PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, DEFAULT, OPEN, TRANSITIVE, PACKAGE_LOCAL, SEALED, NON_SEALED
  })
  @interface ModifierConstant { }
}