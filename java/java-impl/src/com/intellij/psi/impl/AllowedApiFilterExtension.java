/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
 * User: anna
 * Date: 28-May-2010
 */
package com.intellij.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class AllowedApiFilterExtension {
  public static final ExtensionPointName<AllowedApiFilterExtension> EP_NAME = ExtensionPointName.create("com.intellij.allowedApiFilter");

  public abstract boolean isClassForbidden(@NotNull String fqn, @NotNull PsiElement place);

  public static boolean isClassAllowed(@NotNull String fqn, @NotNull PsiElement place) {
    for (AllowedApiFilterExtension extension : Extensions.getExtensions(EP_NAME)) {
      if (extension.isClassForbidden(fqn, place)) return false;
    }
    return true;
  }
}
