// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IStubFileElementType;
import org.jetbrains.annotations.NotNull;

public class PsiJavaFileStubImpl extends PsiFileStubImpl<PsiJavaFile> implements PsiJavaFileStub {
  private final String myPackageName;
  private final LanguageLevel myLanguageLevel;
  private final boolean myCompiled;
  private StubPsiFactory myFactory;

  public PsiJavaFileStubImpl(String packageName, boolean compiled) {
    this(null, packageName, null, compiled);
  }

  public PsiJavaFileStubImpl(PsiJavaFile file, String packageName, LanguageLevel languageLevel, boolean compiled) {
    super(file);
    myPackageName = packageName;
    myLanguageLevel = languageLevel;
    myCompiled = compiled;
    myFactory = compiled ? ClsStubPsiFactory.INSTANCE : SourceStubPsiFactory.INSTANCE;
  }

  @Override
  public @NotNull IStubFileElementType<?> getType() {
    return JavaParserDefinition.JAVA_FILE;
  }

  @Override
  public PsiClass @NotNull [] getClasses() {
    return getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
  }

  @Override
  public PsiJavaModule getModule() {
    StubElement<PsiJavaModule> moduleStub = findChildStubByType(JavaStubElementTypes.MODULE);
    return moduleStub != null ? moduleStub.getPsi() : null;
  }

  @Override
  public String getPackageName() {
    return myPackageName;
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
    if (myPackageName != null ? !myPackageName.equals(stub.myPackageName) : stub.myPackageName != null) return false;
    if (myLanguageLevel != stub.myLanguageLevel) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPackageName != null ? myPackageName.hashCode() : 0;
    result = 31 * result + (myLanguageLevel != null ? myLanguageLevel.hashCode() : 0);
    result = 31 * result + (myCompiled ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "PsiJavaFileStub [" + myPackageName + "]";
  }
}