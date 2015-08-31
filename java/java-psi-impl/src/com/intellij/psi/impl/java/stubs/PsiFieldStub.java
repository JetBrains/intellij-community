/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface PsiFieldStub extends NamedStub<PsiField> {
  @NonNls String INITIALIZER_TOO_LONG = ";INITIALIZER_TOO_LONG;";
  @NonNls String INITIALIZER_NOT_STORED = ";INITIALIZER_NOT_STORED;";

  @NotNull TypeInfo getType(boolean doResolve);
  String getInitializerText();
  boolean isEnumConstant();
  boolean isDeprecated();
  boolean hasDeprecatedAnnotation();
  boolean hasDocComment();
}
