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

public class IntersectionPackageSet implements PackageSet {
  private final PackageSet myFirstSet;
  private final PackageSet mySecondSet;

  public IntersectionPackageSet(PackageSet firstSet, PackageSet secondSet) {
    myFirstSet = firstSet;
    mySecondSet = secondSet;
  }

  public boolean contains(PsiFile file, NamedScopesHolder holder) {
    return myFirstSet.contains(file, holder) && mySecondSet.contains(file, holder);
  }

  public PackageSet createCopy() {
    return new IntersectionPackageSet(myFirstSet.createCopy(), mySecondSet.createCopy());
  }

  public int getNodePriority() {
    return 2;
  }

  public String getText() {
    StringBuffer buf = new StringBuffer();
    boolean needParen = myFirstSet.getNodePriority() > getNodePriority();
    if (needParen) buf.append('(');
    buf.append(myFirstSet.getText());
    if (needParen) buf.append(')');
    buf.append("&&");
    needParen = mySecondSet.getNodePriority() > getNodePriority();
    if (needParen) buf.append('(');
    buf.append(mySecondSet.getText());
    if (needParen) buf.append(')');

    return buf.toString();
  }

  public PackageSet getFirstSet() {
    return myFirstSet;
  }

  public PackageSet getSecondSet() {
    return mySecondSet;
  }
}
