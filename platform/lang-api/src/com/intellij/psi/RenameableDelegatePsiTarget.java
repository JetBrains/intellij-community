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
package com.intellij.psi;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class RenameableDelegatePsiTarget extends DelegatePsiTarget implements PomRenameableTarget<RenameableDelegatePsiTarget>{
  public RenameableDelegatePsiTarget(@NotNull PsiNamedElement element) {
    super(element);
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  @Override
  public RenameableDelegatePsiTarget setName(@NotNull String newName) {
    ((PsiNamedElement)getNavigationElement()).setName(newName);
    return this;
  }

  @Override
  @NotNull
  public String getName() {
    return StringUtil.notNullize(((PsiNamedElement)getNavigationElement()).getName());
  }
}
