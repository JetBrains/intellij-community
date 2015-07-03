/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.StringRef;

/**
 * @author max
 */
public class PsiJavaFileStubImpl extends PsiFileStubImpl<PsiJavaFile> implements PsiJavaFileStub {
  private final StringRef myPackageName;
  private final boolean myCompiled;
  private StubPsiFactory myFactory;

  public PsiJavaFileStubImpl(PsiJavaFile file, StringRef packageName, boolean compiled) {
    super(file);
    myPackageName = packageName;
    myCompiled = compiled;
    myFactory = compiled ? new ClsStubPsiFactory() : new SourceStubPsiFactory();
  }

  public PsiJavaFileStubImpl(String packageName, boolean compiled) {
    this(null, StringRef.fromString(packageName), compiled);
  }

  @Override
  public IStubFileElementType getType() {
    return JavaStubElementTypes.JAVA_FILE;
  }

  @Override
  public PsiClass[] getClasses() {
    return getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
  }

  @Override
  public String getPackageName() {
    return StringRef.toString(myPackageName);
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

  /** @deprecated use constructors (to be removed in IDEA 16) */
  @SuppressWarnings("unused")
  public void setPackageName(String packageName) {
    throw new IncorrectOperationException();
  }

  @Override
  public String toString() {
    return "PsiJavaFileStub [" + myPackageName + "]";
  }
}
