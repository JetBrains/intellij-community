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
    return JavaStubElementTypes.FILE;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PsiJavaFileStub [" + myPackageName + "]";
  }

  public PsiClass[] getClasses() {
    return getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
  }
}