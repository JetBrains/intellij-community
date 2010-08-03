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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class UnionPackageSet implements PackageSet {
  private final PackageSet myFirstSet;
  private final PackageSet mySecondSet;

  public UnionPackageSet(@NotNull PackageSet set1, @NotNull PackageSet set2) {
    myFirstSet = set1;
    mySecondSet = set2;
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    return myFirstSet.contains(file, holder) || mySecondSet.contains(file, holder);
  }

  public PackageSet createCopy() {
    return new UnionPackageSet(myFirstSet.createCopy(), mySecondSet.createCopy());
  }

  public int getNodePriority() {
    return 3;
  }

  public String getText() {
    return myFirstSet.getText() + "||" + mySecondSet.getText();
  }

  public PackageSet getFirstSet() {
    return myFirstSet;
  }

  public PackageSet getSecondSet() {
    return mySecondSet;
  }
}