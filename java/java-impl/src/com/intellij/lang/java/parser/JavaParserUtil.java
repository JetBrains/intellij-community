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
package com.intellij.lang.java.parser;

import com.intellij.lang.*;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class JavaParserUtil {
  private static final Key<LanguageLevel> LANG_LEVEL_KEY = Key.create("JavaParserUtil.LanguageLevel");

  private JavaParserUtil() { }

  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    builder.putUserData(LANG_LEVEL_KEY, level);
  }

  public static boolean areTypeAnnotationsSupported(final PsiBuilder builder) {
    final LanguageLevel level = getLanguageLevel(builder);
    return level.isAtLeast(LanguageLevel.JDK_1_7);
  }

  @NotNull
  private static LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    final LanguageLevel level = builder.getUserData(LANG_LEVEL_KEY);
    assert level != null : builder;
    return level;
  }

  // used instead of PsiBuilder.error() as it drops all but first subsequent error messages
  public static void error(final PsiBuilder builder, final String message) {
    builder.mark().error(message);
  }

  public static void error(final PsiBuilder builder, final String message, @Nullable final PsiBuilder.Marker before) {
    if (before == null) {
      error(builder, message);
    }
    else {
      before.precede().errorBefore(message, before);
    }
  }

  public static boolean expectOrError(final PsiBuilder builder, final IElementType expectedType, final String errorMessage) {
    if (!PsiBuilderUtil.expect(builder, expectedType)) {
      error(builder, errorMessage);
      return false;
    }
    return true;
  }

  public static void emptyElement(final PsiBuilder builder, final IElementType type) {
    builder.mark().done(type);
  }

  public static void emptyElement(final PsiBuilder.Marker before, final IElementType type) {
    before.precede().doneBefore(type, before);
  }

  public static PsiBuilder braceMatchingBuilder(final PsiBuilder builder) {
    return new PsiBuilderAdapter(builder) {
      private int braceCount = 1;
      private int lastOffset = -1;

      @Override
      public IElementType getTokenType() {
        final IElementType tokenType = super.getTokenType();
        if (getCurrentOffset() != lastOffset) {
          if (tokenType == JavaTokenType.LBRACE) {
            braceCount++;
          }
          else if (tokenType == JavaTokenType.RBRACE) {
            braceCount--;
          }
          lastOffset = getCurrentOffset();
        }
        return (braceCount == 0 ? null : tokenType);
      }
    };
  }

  public static class PsiBuilderAdapter implements PsiBuilder {
    protected final PsiBuilder myDelegate;

    public PsiBuilderAdapter(final PsiBuilder delegate) {
      myDelegate = delegate;
    }

    public CharSequence getOriginalText() {
      return myDelegate.getOriginalText();
    }

    public void advanceLexer() {
      myDelegate.advanceLexer();
    }

    @Nullable
    public IElementType getTokenType() {
      return myDelegate.getTokenType();
    }

    public void setTokenTypeRemapper(final ITokenTypeRemapper remapper) {
      myDelegate.setTokenTypeRemapper(remapper);
    }

    @Nullable @NonNls
    public String getTokenText() {
      return myDelegate.getTokenText();
    }

    public int getCurrentOffset() {
      return myDelegate.getCurrentOffset();
    }

    public Marker mark() {
      return myDelegate.mark();
    }

    public void error(final String messageText) {
      myDelegate.error(messageText);
    }

    public boolean eof() {
      return myDelegate.eof();
    }

    public ASTNode getTreeBuilt() {
      return myDelegate.getTreeBuilt();
    }

    public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
      return myDelegate.getLightTree();
    }

    public void setDebugMode(final boolean dbgMode) {
      myDelegate.setDebugMode(dbgMode);
    }

    public void enforceCommentTokens(final TokenSet tokens) {
      myDelegate.enforceCommentTokens(tokens);
    }

    @Nullable
    public LighterASTNode getLatestDoneMarker() {
      return myDelegate.getLatestDoneMarker();
    }

    @Nullable
    public <T> T getUserData(@NotNull final Key<T> key) {
      return myDelegate.getUserData(key);
    }

    public <T> void putUserData(@NotNull final Key<T> key, @Nullable final T value) {
      myDelegate.putUserData(key, value);
    }
  }
}
