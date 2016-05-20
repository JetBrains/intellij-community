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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiLiteralStub extends StubBase<PsiLiteralExpressionImpl> {
  @NotNull private final String myLiteralText;
  private volatile IElementType myLiteralType;

  public PsiLiteralStub(StubElement parent, @NotNull String literalText) {
    super(parent, JavaStubElementTypes.LITERAL_EXPRESSION);
    myLiteralText = literalText;
  }

  @NotNull
  public String getLiteralText() {
    return myLiteralText;
  }

  @NotNull
  public IElementType getLiteralType() {
    IElementType type = myLiteralType;
    if (type == null) {
      JavaLexer lexer = new JavaLexer(LanguageLevel.HIGHEST);
      lexer.start(myLiteralText);
      myLiteralType = type = lexer.getTokenType();
      assert type != null : myLiteralText;
    }
    return type;
  }
}
