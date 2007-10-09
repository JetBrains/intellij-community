/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/**
 * Provides a list of possible modifier keywords for Java classes, methods and fields.
 */
@SuppressWarnings({"HardCodedStringLiteral", "JavaDoc"})
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
}
