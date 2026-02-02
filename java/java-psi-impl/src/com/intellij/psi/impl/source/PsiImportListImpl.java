// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportListStub;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class PsiImportListImpl extends JavaStubPsiElement<PsiImportListStub> implements PsiImportList {
  private volatile Map<String,PsiImportStatement> myClassNameToImportMap;
  private volatile Map<String,PsiImportStatement> myPackageNameToImportMap;
  private volatile Map<String,PsiImportModuleStatement> myModuleNameToImportMap;
  private volatile Map<String,PsiImportStatementBase> myNameToSingleImportMap;

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
    clone.myModuleNameToImportMap = null;
    return clone;
  }

  @Override
  public void subtreeChanged() {
    myClassNameToImportMap = null;
    myPackageNameToImportMap = null;
    myNameToSingleImportMap = null;
    myModuleNameToImportMap = null;
    super.subtreeChanged();
  }

  private static final TokenSet IMPORT_STATEMENT_BIT_SET = TokenSet.create(JavaElementType.IMPORT_STATEMENT);
  private static final TokenSet IMPORT_STATIC_STATEMENT_BIT_SET = TokenSet.create(JavaElementType.IMPORT_STATIC_STATEMENT);
  private static final TokenSet IMPORT_MODULE_STATEMENT_BIT_SET = TokenSet.create(JavaElementType.IMPORT_MODULE_STATEMENT);

  @Override
  public PsiImportStatement @NotNull [] getImportStatements() {
    return getStubOrPsiChildren(IMPORT_STATEMENT_BIT_SET, PsiImportStatementImpl.ARRAY_FACTORY);
  }

  @Override
  public PsiImportStaticStatement @NotNull [] getImportStaticStatements() {
    return getStubOrPsiChildren(IMPORT_STATIC_STATEMENT_BIT_SET, PsiImportStaticStatementImpl.ARRAY_FACTORY);
  }

  @Override
  public PsiImportModuleStatement @NotNull [] getImportModuleStatements() {
    return getStubOrPsiChildren(IMPORT_MODULE_STATEMENT_BIT_SET, PsiImportModuleStatementImpl.ARRAY_FACTORY);
  }

  @Override
  public PsiImportStatementBase @NotNull [] getAllImportStatements() {
    return getStubOrPsiChildren(ElementType.IMPORT_STATEMENT_BASE_BIT_SET, PsiImportStatementBase.ARRAY_FACTORY);
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
  public @Nullable PsiImportModuleStatement findImportModuleStatement(String name) {
    while (true) {
      Map<String, PsiImportModuleStatement> map = myModuleNameToImportMap;
      if (map == null) {
        initializeMaps();
      }
      else {
        return map.get(name);
      }
    }
  }

  @Override
  public @Nullable PsiImportStatementBase findSingleImportStatement(String name) {
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
    PsiImportStatementBase[] importStatements1 = getAllImportStatements();
    PsiImportStatementBase[] importStatements2 = otherList.getAllImportStatements();
    if (importStatements1.length != importStatements2.length) return false;
    for (int i = 0; i < importStatements1.length; i++) {
      if (!importStatements1[i].isReplaceEquivalent(importStatements2[i])) return false;
    }
    return true;
  }

  private void initializeMaps() {
    Map<String, PsiImportStatement> classNameToImportMap = new HashMap<>();
    Map<String, PsiImportStatement> packageNameToImportMap = new HashMap<>();
    Map<String, PsiImportStatementBase> nameToSingleImportMap = new HashMap<>();
    Map<String, PsiImportModuleStatement> moduleNameToImportMap = new HashMap<>();
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

    PsiImportModuleStatement[] importModules = getImportModuleStatements();
    for (PsiImportModuleStatement importModule : importModules) {
      String referenceName = importModule.getReferenceName();
      if (referenceName != null) {
        moduleNameToImportMap.put(referenceName, importModule);
      }
    }

    myClassNameToImportMap = classNameToImportMap;
    myPackageNameToImportMap = packageNameToImportMap;
    myNameToSingleImportMap = nameToSingleImportMap;
    myModuleNameToImportMap = moduleNameToImportMap;
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
