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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;

public class PsiJavaFileStubImpl extends PsiFileStubImpl<PsiJavaFile> implements PsiJavaFileStub {
  private StringRef myPackageName;
  private final boolean myCompiled;

  public PsiJavaFileStubImpl(final PsiJavaFile file, boolean compiled) {
    super(file);
    myPackageName = StringRef.fromString(file.getPackageName());
    myCompiled = compiled;
  }

  public PsiJavaFileStubImpl(final String packageName, boolean compiled) {
    this(StringRef.fromString(packageName), compiled);
  }

  public PsiJavaFileStubImpl(final StringRef packageName, boolean compiled) {
    super(null);
    myPackageName = packageName;
    myCompiled = compiled;
  }

  public String getPackageName() {
    return StringRef.toString(myPackageName);
  }

  public boolean isCompiled() {
    return myCompiled;
  }

  public void setPackageName(final String packageName) {
    myPackageName = StringRef.fromString(packageName);
  }

  public IStubFileElementType getType() {
    return JavaStubElementTypes.JAVA_FILE;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiJavaFileStub [" + myPackageName + "]";
  }

  public PsiClass[] getClasses() {
    return getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
  }
}