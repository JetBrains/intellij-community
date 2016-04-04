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

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.StringRef;

/**
 * @author max
 */
public class PsiClassReferenceListStubImpl extends StubBase<PsiReferenceList> implements PsiClassReferenceListStub {
  private final PsiReferenceList.Role myRole;
  private final StringRef[] myNames;
  private PsiClassType[] myTypes;

  public PsiClassReferenceListStubImpl(final JavaClassReferenceListElementType type, final StubElement parent, final String[] names, final PsiReferenceList.Role role) {
    super(parent, type);
    myNames = StringRef.createArray(names.length);
    for (int i = 0; i < names.length; i++) {
      myNames[i] = StringRef.fromString(names[i]);
    }
    myRole = role;
  }

  public PsiClassReferenceListStubImpl(final JavaClassReferenceListElementType type, final StubElement parent, final StringRef[] names, final PsiReferenceList.Role role) {
    super(parent, type);
    myNames = names;
    myRole = role;
  }

  @Override
  public PsiClassType[] getReferencedTypes() {
    if (myTypes != null) return myTypes;
    if (myNames.length == 0) {
      myTypes = PsiClassType.EMPTY_ARRAY;
      return myTypes;
    }

    PsiClassType[] types = new PsiClassType[myNames.length];

    final boolean compiled = ((JavaClassReferenceListElementType)getStubType()).isCompiled(this);
    if (compiled) {
      for (int i = 0; i < types.length; i++) {
        types[i] = new PsiClassReferenceType(new ClsJavaCodeReferenceElementImpl(getPsi(), StringRef.toString(myNames[i])), null);
      }
    }
    else {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();

      int nullCount = 0;
      final PsiReferenceList psi = getPsi();
      for (int i = 0; i < types.length; i++) {
        PsiElement context = psi;
        if (getParentStub() instanceof PsiClassStub) {
          context = ((PsiClassImpl)getParentStub().getPsi()).calcBasesResolveContext(PsiNameHelper.getShortClassName(StringRef.toString(myNames[i])), psi);
        }

        try {
          final PsiJavaCodeReferenceElement ref = factory.createReferenceFromText(StringRef.toString(myNames[i]), context);
          ((PsiJavaCodeReferenceElementImpl)ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND);
          types[i] = factory.createType(ref);
        }
        catch (IncorrectOperationException e) {
          types[i] = null;
          nullCount++;
        }
      }

      if (nullCount > 0) {
        PsiClassType[] newTypes = new PsiClassType[types.length - nullCount];
        int cnt = 0;
        for (PsiClassType type : types) {
          if (type != null) newTypes[cnt++] = type;
        }
        types = newTypes;
      }
    }

    myTypes = types;
    return types;
  }

  @Override
  public String[] getReferencedNames() {
    String[] names = ArrayUtil.newStringArray(myNames.length);
    for (int i = 0; i < names.length; i++) {
      names[i] = StringRef.toString(myNames[i]);
    }
    return names;
  }

  @Override
  public PsiReferenceList.Role getRole() {
    return myRole;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiRefListStub[").append(myRole.name()).append(":");
    for (int i = 0; i < myNames.length; i++) {
      if (i > 0) builder.append(", ");
      builder.append(myNames[i]);
    }
    builder.append("]");
    return builder.toString();
  }
}
