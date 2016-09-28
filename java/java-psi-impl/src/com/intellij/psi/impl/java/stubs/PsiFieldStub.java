/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.TypeInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public interface PsiFieldStub extends PsiMemberStub<PsiField> {
  String INITIALIZER_TOO_LONG = ";INITIALIZER_TOO_LONG;";
  String INITIALIZER_NOT_STORED = ";INITIALIZER_NOT_STORED;";

  @NotNull TypeInfo getType(boolean doResolve);
  String getInitializerText();
  boolean isEnumConstant();
  boolean hasDeprecatedAnnotation();
  boolean hasDocComment();
}