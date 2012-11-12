/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;

/**
 * Provides a list of possible modifier keywords for Java classes, methods and fields.
 */
public interface PsiModifier {
  @NonNls String PUBLIC = "public";
  @NonNls String PROTECTED = "protected";
  @NonNls String PRIVATE = "private";
  @NonNls String PACKAGE_LOCAL = "packageLocal";
  @NonNls String STATIC = "static";
  @NonNls String ABSTRACT = "abstract";
  @NonNls String FINAL = "final";
  @NonNls String NATIVE = "native";
  @NonNls String SYNCHRONIZED = "synchronized";
  @NonNls String STRICTFP = "strictfp";
  @NonNls String TRANSIENT = "transient";
  @NonNls String VOLATILE = "volatile";
  @NonNls String DEFAULT = "default";

  @NonNls String[] MODIFIERS = {
    PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, DEFAULT
  };

  @MagicConstant(stringValues = {
    PUBLIC, PROTECTED, PRIVATE, STATIC, ABSTRACT, FINAL, NATIVE, SYNCHRONIZED, STRICTFP, TRANSIENT, VOLATILE, DEFAULT, PACKAGE_LOCAL
  })
  @interface ModifierConstant { }
}
