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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class PsiTypeParameterStubImpl extends StubBase<PsiTypeParameter> implements PsiTypeParameterStub {
  private final String myName;

  public PsiTypeParameterStubImpl(StubElement parent, String name) {
    super(parent, JavaStubElementTypes.TYPE_PARAMETER);
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiTypeParameter[" + myName + ']';
  }
  
  @Override
  @NotNull
  public List<PsiAnnotationStub> getAnnotations() {
    List<StubElement> children = getChildrenStubs();

    return ContainerUtil.mapNotNull(children,
                                    stubElement -> stubElement instanceof PsiAnnotationStub ? (PsiAnnotationStub)stubElement : null);
  }
}