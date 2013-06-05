/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class LightKeyword extends LightElement implements PsiKeyword, PsiJavaToken {
  private final String myText;

  public LightKeyword(PsiManager manager, String text) {
    super(manager, JavaLanguage.INSTANCE);
    myText = text;
  }

  @Override
  public String getText(){
    return myText;
  }

  @Override
  public IElementType getTokenType(){
    Lexer lexer = JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);
    lexer.start(myText);
    return lexer.getTokenType();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitKeyword(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement copy(){
    return new LightKeyword(getManager(), myText);
  }

  public String toString(){
    return "PsiKeyword:" + getText();
  }
}
