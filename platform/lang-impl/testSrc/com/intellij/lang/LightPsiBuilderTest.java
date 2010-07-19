/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class LightPsiBuilderTest {

  @Test
  public void testPlain() {
    doTest(
      "a<<b",
      new PsiParser() {
        @NotNull
        public ASTNode parse(IElementType root, PsiBuilder builder) {
          final PsiBuilder.Marker rootMarker = builder.mark();
          while (builder.getTokenType() != null) {
            builder.advanceLexer();
          }
          rootMarker.done(root);
          return builder.getTreeBuilt();
        }
      },
      "Element(ROOT)\n" +
      "  PsiElement(TOKEN)('a')\n" +
      "  PsiElement(TOKEN)('<')\n" +
      "  PsiElement(TOKEN)('<')\n" +
      "  PsiElement(TOKEN)('b')\n"
    );
  }

  @Test
  public void testCollapse() {
    doTest(
      "a<<b",
      new PsiParser() {
        @NotNull
        public ASTNode parse(IElementType root, PsiBuilder builder) {
          final PsiBuilder.Marker rootMarker = builder.mark();
          PsiBuilder.Marker inner = null;
          while (builder.getTokenType() != null) {
            if ("<".equals(builder.getTokenText()) && inner == null) inner = builder.mark();
            builder.advanceLexer();
            if (!"<".equals(builder.getTokenText()) && inner != null) { inner.collapse(new IElementType("COLLAPSE", Language.ANY)); inner = null; }
          }
          rootMarker.done(root);
          return builder.getTreeBuilt();
        }
      },
      "Element(ROOT)\n" +
      "  PsiElement(TOKEN)('a')\n" +
      "  PsiElement(COLLAPSE)('<<')\n" +
      "  PsiElement(TOKEN)('b')\n"
    );
  }


  private static void doTest(final String text, final PsiParser parser, final String expected) {
    final PsiBuilder builder = new PsiBuilderImpl(new MyTestLexer(), TokenSet.EMPTY, TokenSet.EMPTY, text);
    final ASTNode root = parser.parse(new IElementType("ROOT", Language.ANY), builder);
    assertEquals(expected, DebugUtil.nodeTreeToString(root, true));
  }

  private static class MyTestLexer extends LexerBase {
    public static IElementType TOKEN = new IElementType("TOKEN", Language.ANY);

    private CharSequence myBuffer = "";
    private int myIndex = 0;
    private int myBufferEnd = 1;

    public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer.subSequence(startOffset, endOffset);
      myIndex = 0;
      myBufferEnd = myBuffer.length();
    }

    public int getState() {
      return 0;
    }

    public IElementType getTokenType() {
      return myIndex < myBufferEnd ? TOKEN : null;
    }

    public int getTokenStart() {
      return myIndex;
    }

    public int getTokenEnd() {
      return myIndex + 1;
    }

    public void advance() {
      if (myIndex < myBufferEnd) myIndex++;
    }

    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    public int getBufferEnd() {
      return myBufferEnd;
    }
  }
}
