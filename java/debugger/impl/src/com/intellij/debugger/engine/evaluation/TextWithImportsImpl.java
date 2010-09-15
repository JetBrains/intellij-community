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
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class TextWithImportsImpl implements TextWithImports{

  private final CodeFragmentKind myKind;
  private String myText;
  private final String myImports;


  public TextWithImportsImpl (PsiExpression expression) {
    myKind = CodeFragmentKind.EXPRESSION;
    final String text = expression.getText();
    PsiFile containingFile = expression.getContainingFile();
    if(containingFile instanceof PsiExpressionCodeFragment) {
      myText = text;
      myImports = ((JavaCodeFragment)containingFile).importsToString();
    }
    else {
      final int separatorIndex = text.indexOf(DebuggerEditorImpl.SEPARATOR);
      if(separatorIndex >= 0){
        myText = text.substring(0, separatorIndex);
        myImports = text.substring(separatorIndex + 1);
      }
      else {
        myText = text;
        myImports = "";
      }
    }
  }

  public TextWithImportsImpl (CodeFragmentKind kind, @NotNull String text, @NotNull String imports) {
    myKind = kind;
    myText = text;
    myImports = imports;
  }

  public TextWithImportsImpl(CodeFragmentKind kind, @NotNull String text) {
    myKind = kind;
    text = text.trim();
    final int separatorIndex = text.indexOf(DebuggerEditorImpl.SEPARATOR);
    if(separatorIndex >= 0){
      myText = text.substring(0, separatorIndex);
      myImports = text.substring(separatorIndex + 1);
    }
    else {
      myText = text;
      myImports = "";
    }
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
    return "".equals(myImports) ? myText : myText + DebuggerEditorImpl.SEPARATOR + myImports;
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

}
