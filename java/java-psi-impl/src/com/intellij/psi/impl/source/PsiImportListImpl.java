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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportListStub;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PsiImportListImpl extends JavaStubPsiElement<PsiImportListStub> implements PsiImportList {
  private volatile Map<String,PsiImportStatement> myClassNameToImportMap;
  private volatile Map<String,PsiImportStatement> myPackageNameToImportMap;
  private volatile Map<String,PsiImportStatementBase> myNameToSingleImportMap;
  private static final PsiImportStatementBase[] EMPTY_ARRAY = PsiImportStatementBase.EMPTY_ARRAY;
  private static final ArrayFactory<PsiImportStatementBase> ARRAY_FACTORY =
    count -> count == 0 ? EMPTY_ARRAY : new PsiImportStatementBase[count];

  public PsiImportListImpl(PsiImportListStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_LIST);
  }

  public PsiImportListImpl(ASTNode node) {
    super(node);
  }

  @Override
  protected Object clone() {
    PsiImportListImpl clone = (PsiImportListImpl)super.clone();
    clone.myClassNameToImportMap = null;
    clone.myPackageNameToImportMap = null;
    clone.myNameToSingleImportMap = null;
    return clone;
  }

  @Override
  public void subtreeChanged() {
    myClassNameToImportMap = null;
    myPackageNameToImportMap = null;
    myNameToSingleImportMap = null;
    super.subtreeChanged();
  }

  private static final TokenSet IMPORT_STATEMENT_BIT_SET = TokenSet.create(JavaElementType.IMPORT_STATEMENT);
  private static final TokenSet IMPORT_STATIC_STATEMENT_BIT_SET = TokenSet.create(JavaElementType.IMPORT_STATIC_STATEMENT);

  @Override
  public PsiImportStatement @NotNull [] getImportStatements() {
    return getStubOrPsiChildren(IMPORT_STATEMENT_BIT_SET, PsiImportStatementImpl.ARRAY_FACTORY);
  }

  @Override
  public PsiImportStaticStatement @NotNull [] getImportStaticStatements() {
    return getStubOrPsiChildren(IMPORT_STATIC_STATEMENT_BIT_SET, PsiImportStaticStatementImpl.ARRAY_FACTORY);
  }

  @Override
  public PsiImportStatementBase @NotNull [] getAllImportStatements() {
    return getStubOrPsiChildren(ElementType.IMPORT_STATEMENT_BASE_BIT_SET, ARRAY_FACTORY);
  }

  @Override
  public PsiImportStatement findSingleClassImportStatement(String name) {
    while (true) {
      Map<String, PsiImportStatement> map = myClassNameToImportMap;
      if (map == null) {
        initializeMaps();
      }
      else {
        return map.get(name);
      }
    }
  }

  @Override
  public PsiImportStatement findOnDemandImportStatement(String name) {
    while (true) {
      Map<String, PsiImportStatement> map = myPackageNameToImportMap;
      if (map == null) {
        initializeMaps();
      }
      else {
        return map.get(name);
      }
    }
  }

  @Override
  public PsiImportStatementBase findSingleImportStatement(String name) {
    while (true) {
      Map<String, PsiImportStatementBase> map = myNameToSingleImportMap;
      if (map == null) {
        initializeMaps();
      }
      else {
        return map.get(name);
      }
    }
  }

  @Override
  public boolean isReplaceEquivalent(PsiImportList otherList) {
    return getText().equals(otherList.getText());
  }

  private void initializeMaps() {
    Map<String, PsiImportStatement> classNameToImportMap = new HashMap<>();
    Map<String, PsiImportStatement> packageNameToImportMap = new HashMap<>();
    Map<String, PsiImportStatementBase> nameToSingleImportMap = new HashMap<>();
    PsiImportStatement[] imports = getImportStatements();
    for (PsiImportStatement anImport : imports) {
      String qName = anImport.getQualifiedName();
      if (qName == null) continue;
      if (anImport.isOnDemand()) {
        packageNameToImportMap.put(qName, anImport);
      }
      else {
        classNameToImportMap.put(qName, anImport);
        PsiJavaCodeReferenceElement importReference = anImport.getImportReference();
        if (importReference == null) continue;
        nameToSingleImportMap.put(importReference.getReferenceName(), anImport);
      }
    }

    PsiImportStaticStatement[] importStatics = getImportStaticStatements();
    for (PsiImportStaticStatement importStatic : importStatics) {
      if (!importStatic.isOnDemand()) {
        String referenceName = importStatic.getReferenceName();
        if (referenceName != null) {
          nameToSingleImportMap.put(referenceName, importStatic);
        }
      }
    }

    myClassNameToImportMap = classNameToImportMap;
    myPackageNameToImportMap = packageNameToImportMap;
    myNameToSingleImportMap = nameToSingleImportMap;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiImportList";
  }
}
