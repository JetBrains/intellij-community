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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.impl.java.stubs.PsiPackageAccessibilityStatementStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

import java.util.Collections;
import java.util.List;

import static com.intellij.psi.impl.java.stubs.JavaPackageAccessibilityStatementElementType.typeToRole;
import static com.intellij.util.ObjectUtils.notNull;

public class PsiPackageAccessibilityStatementStubImpl extends StubBase<PsiPackageAccessibilityStatement> implements PsiPackageAccessibilityStatementStub {
  private final String myPackageName;
  private final List<String> myTargets;

  public PsiPackageAccessibilityStatementStubImpl(StubElement parent, IStubElementType type, String packageName, List<String> targets) {
    super(parent, type);
    myPackageName = notNull(packageName, "");
    myTargets = targets == null || targets.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(targets);
  }

  @Override
  public String getPackageName() {
    return myPackageName;
  }

  @Override
  public List<String> getTargets() {
    return myTargets;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PsiPackageAccessibilityStatementStub[").append(typeToRole(getStubType())).append("]:").append(myPackageName);
    for (String target : myTargets) sb.append(':').append(target);
    return sb.toString();
  }
}