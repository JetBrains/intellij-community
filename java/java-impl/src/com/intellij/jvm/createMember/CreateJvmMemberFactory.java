/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jvm.createMember;

import com.intellij.jvm.JvmClass;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface CreateJvmMemberFactory {

  ExtensionPointName<CreateJvmMemberFactory> EP_NAME = ExtensionPointName.create("com.intellij.jvm.createMember");

  /**
   * @param target  target class. This parameter is WHERE in the API.
   * @param request requested member data. This is WHAT in the API.
   * @param context element to get origin language, project, resolve scope, etc.
   * @return collection of actions. This actions are HOW in the API.
   */
  @NotNull
  Collection<CreateMemberAction> getActions(@NotNull JvmClass target, @NotNull CreateMemberRequest request, @NotNull PsiElement context);
}
