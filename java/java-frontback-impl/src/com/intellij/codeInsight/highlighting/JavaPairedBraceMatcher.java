package com.intellij.codeInsight.highlighting;

import com.intellij.BaseJavaJspElementType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.impl.source.BasicJavaTokenSet;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class JavaPairedBraceMatcher extends PairedBraceAndAnglesMatcher {

  private static class Holder {
    private static final BasicJavaTokenSet TYPE_TOKENS =
      BasicJavaTokenSet.orSet(BaseJavaJspElementType.WHITE_SPACE_BIT_SET,
                              BasicElementTypes.BASIC_JAVA_COMMENT_BIT_SET,
                              BasicJavaTokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.COMMA,
                                                       JavaTokenType.AT,//anno
                                                       JavaTokenType.RBRACKET, JavaTokenType.LBRACKET, //arrays
                                                       JavaTokenType.QUEST, JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.SUPER_KEYWORD));//wildcards
  }

  public JavaPairedBraceMatcher() {
    super(new JavaBraceMatcher(), JavaLanguage.INSTANCE, JavaFileType.INSTANCE, Holder.TYPE_TOKENS.toTokenSet());
  }

  @Override
  public @NotNull IElementType lt() {
    return JavaTokenType.LT;
  }

  @Override
  public @NotNull IElementType gt() {
    return JavaTokenType.GT;
  }
}
