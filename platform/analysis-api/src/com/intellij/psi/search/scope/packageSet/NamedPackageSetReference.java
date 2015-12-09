/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class NamedPackageSetReference extends PackageSetBase {
  private final String myName;

  public NamedPackageSetReference(String name) {
    myName = StringUtil.trimStart(name, "$");
  }

  @Override
  public boolean contains(VirtualFile file, NamedScopesHolder holder) {
    if (holder == null) return false;
    final NamedScope scope = holder.getScope(myName);
    if (scope != null) {
      final PackageSet packageSet = scope.getValue();
      if (packageSet != null) {
        return packageSet instanceof PackageSetBase ? ((PackageSetBase)packageSet).contains(file, holder) : packageSet.contains(getPsiFile(file, holder.getProject()), holder);
      }
    }
    return false;
  }

  @Override
  @NotNull
  public PackageSet createCopy() {
    return new NamedPackageSetReference(myName);
  }

  @Override
  @NotNull
  public String getText() {
    return "$" + myName;
  }

  @Override
  public int getNodePriority() {
    return 0;
  }
}
