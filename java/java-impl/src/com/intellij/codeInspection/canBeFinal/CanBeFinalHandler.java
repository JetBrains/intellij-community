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
package com.intellij.codeInspection.canBeFinal;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiMember;

/**
 * User: anna
 * Date: 1/31/12
 */
public abstract class CanBeFinalHandler {
  public static final ExtensionPointName<CanBeFinalHandler> EP_NAME = ExtensionPointName.create("com.intellij.canBeFinal");

  public abstract boolean canBeFinal(PsiMember member);

  public static boolean allowToBeFinal(PsiMember member) {
    for (CanBeFinalHandler handler : Extensions.getExtensions(EP_NAME)) {
      if (!handler.canBeFinal(member)) return false;
    }
    return true;
  }
}
