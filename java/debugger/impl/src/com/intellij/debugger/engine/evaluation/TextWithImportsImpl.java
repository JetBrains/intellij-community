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
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.ui.DebuggerEditorImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class TextWithImportsImpl implements TextWithImports{

  private final CodeFragmentKind myKind;
  private String myText;
  private final FileType myFileType;
  private final String myImports;

  public TextWithImportsImpl (PsiExpression expression) {
    myKind = CodeFragmentKind.EXPRESSION;
    final String text = expression.getText();
    PsiFile containingFile = expression.getContainingFile();
    if(containingFile instanceof PsiExpressionCodeFragment) {
      myText = text;
      myImports = ((JavaCodeFragment)containingFile).importsToString();
      myFileType = StdFileTypes.JAVA;
    }
    else {
      Trinity<String, String, FileType> trinity = parseExternalForm(text);
      myText = trinity.first;
      myImports = trinity.second;
      myFileType = trinity.third;
    }
  }

  public TextWithImportsImpl (CodeFragmentKind kind, @NotNull String text, @NotNull String imports, @NotNull FileType fileType) {
    myKind = kind;
    myText = text;
    myImports = imports;
    myFileType = fileType;
  }

  public TextWithImportsImpl(CodeFragmentKind kind, @NotNull String text) {
    myKind = kind;
    Trinity<String, String, FileType> trinity = parseExternalForm(text);
    myText = trinity.first;
    myImports = trinity.second;
    myFileType = trinity.third;
  }

  private static Trinity<String, String, FileType> parseExternalForm(String s) {
    String[] split = s.trim().split(String.valueOf(DebuggerEditorImpl.SEPARATOR));
    return Trinity.create(split[0], split.length > 1 ? split[1] : "", split.length > 2 ? FileTypeManager.getInstance().getStdFileType(split[2]) : null);
  }

  public CodeFragmentKind getKind() {
    return myKind;
  }

  public String getText() {
    return myText;
  }

  public @NotNull String getImports() {
    return myImports;
  }

  public boolean equals(Object object) {
    if(!(object instanceof TextWithImportsImpl)) {
      return false;
    }
    TextWithImportsImpl item = ((TextWithImportsImpl)object);
    return Comparing.equal(item.myText, myText) && Comparing.equal(item.myImports, myImports);
  }

  public String toString() {
    return getText();
  }

  public String toExternalForm() {
    String result = myText;
    if (StringUtil.isNotEmpty(myImports) || myFileType != null) {
      result += DebuggerEditorImpl.SEPARATOR + myImports;
    }
    if (myFileType != null) {
      result += DebuggerEditorImpl.SEPARATOR + myFileType.getName();
    }
    return result;
  }

  public int hashCode() {
    return myText.hashCode();
  }

  public boolean isEmpty() {
    final String text = getText();
    return text == null || "".equals(text.trim());
  }

  public void setText(String newText) {
    myText = newText;
  }

  public FileType getFileType() {
    return myFileType;
  }
}
