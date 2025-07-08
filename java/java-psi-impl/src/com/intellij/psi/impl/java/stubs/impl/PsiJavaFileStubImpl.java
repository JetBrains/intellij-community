// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class PsiJavaFileStubImpl extends PsiFileStubImpl<PsiJavaFile> implements PsiJavaFileStub {
  private final LanguageLevel myLanguageLevel;
  private final boolean myCompiled;
  private StubPsiFactory myFactory;

  public PsiJavaFileStubImpl(boolean compiled) {
    this(null, null, compiled);
  }

  /**
   * @deprecated kept for Kotlin plugin compatibility
   */
  @Deprecated
  public PsiJavaFileStubImpl(String ignoredPackageName, boolean compiled) {
    this(null, null, compiled);
  }

  public PsiJavaFileStubImpl(PsiJavaFile file, LanguageLevel languageLevel, boolean compiled) {
    super(file);
    myLanguageLevel = languageLevel;
    myCompiled = compiled;
    myFactory = compiled ? ClsStubPsiFactory.INSTANCE : SourceStubPsiFactory.INSTANCE;
  }

  @Override
  public @NotNull IStubFileElementType<?> getType() {
    throw new UnsupportedOperationException();
  }

  @Override
  public IElementType getElementType() {
    return JavaParserDefinition.JAVA_FILE;
  }

  @Override
  public @NotNull IElementType getFileElementType() {
    return JavaParserDefinition.JAVA_FILE;
  }

  @Override
  public PsiClass @NotNull [] getClasses() {
    return getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
  }

  @Override
  public PsiJavaModule getModule() {
    @SuppressWarnings("SSBasedInspection") StubElement<PsiJavaModule> moduleStub = ObjectUtils.tryCast(findChildStubByElementType(JavaStubElementTypes.MODULE), StubElement.class);
    return moduleStub != null ? moduleStub.getPsi() : null;
  }

  @Override
  public String getPackageName() {
    PsiPackageStatementStub stub = (PsiPackageStatementStub)(StubElement<?>)findChildStubByElementType(JavaElementType.PACKAGE_STATEMENT);
    return stub == null ? "" : stub.getPackageName();
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public boolean isCompiled() {
    return myCompiled;
  }

  @Override
  public StubPsiFactory getPsiFactory() {
    return myFactory;
  }

  @Override
  public void setPsiFactory(StubPsiFactory factory) {
    myFactory = factory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PsiJavaFileStubImpl stub = (PsiJavaFileStubImpl)o;

    if (myCompiled != stub.myCompiled) return false;
    if (myLanguageLevel != stub.myLanguageLevel) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    result = 31 * result + (myLanguageLevel != null ? myLanguageLevel.hashCode() : 0);
    result = 31 * result + (myCompiled ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "PsiJavaFileStub [" + getPackageName() + "]";
  }
}