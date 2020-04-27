// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class PsiClassReferenceListStubImpl extends StubBase<PsiReferenceList> implements PsiClassReferenceListStub {
  private final String[] myNames;
  private volatile PsiClassType[] myTypes;

  public PsiClassReferenceListStubImpl(@NotNull JavaClassReferenceListElementType type, StubElement parent, String @NotNull [] names) {
    super(parent, type);
    ObjectUtils.assertAllElementsNotNull(names);
    myNames = names;
  }

  @Override
  public PsiClassType @NotNull [] getReferencedTypes() {
    PsiClassType[] types = myTypes;
    if (types == null) {
      myTypes = types = createTypes();
    }
    return types.clone();
  }

  private PsiClassType @NotNull [] createTypes() {
    PsiClassType[] types = myNames.length == 0 ? PsiClassType.EMPTY_ARRAY : new PsiClassType[myNames.length];

    final boolean compiled = ((JavaClassReferenceListElementType)getStubType()).isCompiled(this);
    if (compiled) {
      for (int i = 0; i < types.length; i++) {
        types[i] = new PsiClassReferenceType(new ClsJavaCodeReferenceElementImpl(getPsi(), myNames[i]), null);
      }
    }
    else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());

      int nullCount = 0;
      final PsiReferenceList psi = getPsi();
      for (int i = 0; i < types.length; i++) {
        try {
          final PsiJavaCodeReferenceElement ref = factory.createReferenceFromText(myNames[i], psi);
          ((PsiJavaCodeReferenceElementImpl)ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.Kind.CLASS_NAME_KIND);
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
    return types;
  }

  @Override
  public String @NotNull [] getReferencedNames() {
    return myNames.clone();
  }

  @NotNull
  @Override
  public PsiReferenceList.Role getRole() {
    return JavaClassReferenceListElementType.elementTypeToRole(getStubType());
  }

  @Override
  public String toString() {
    return "PsiRefListStub[" + getRole() + ':' + String.join(", ", myNames) + ']';
  }
}