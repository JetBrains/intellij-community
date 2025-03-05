// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JShellElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * @author Eugene Zhuravlev
 */
public class JShellParser extends JavaParser {
  public static final JShellParser INSTANCE = new JShellParser();

  private static final TokenSet TOP_LEVEL_DECLARATIONS =
    TokenSet.create(JavaElementType.FIELD, JavaElementType.METHOD, JavaElementType.CLASS);
  private static final Predicate<IElementType> IMPORT_PARSED_CONDITION = tokenType -> JavaElementType.IMPORT_STATEMENT == tokenType;
  private static final Predicate<IElementType> DECLARATION_PARSED_CONDITION = tokenType -> TOP_LEVEL_DECLARATIONS.contains(tokenType);

  private final FileParser myJShellFileParser = new FileParser(this) {
    private final TokenSet IMPORT_PARSING_STOP_LIST = TokenSet.orSet(
      IMPORT_LIST_STOPPER_SET,
      TokenSet.orSet(
        ElementType.MODIFIER_BIT_SET,
        ElementType.JAVA_COMMENT_BIT_SET,
        ElementType.EXPRESSION_BIT_SET,
        ElementType.JAVA_STATEMENT_BIT_SET,
        ElementType.PRIMITIVE_TYPE_BIT_SET,
        TokenSet.create(JShellElementType.ROOT_CLASS, JavaTokenType.IDENTIFIER)
      )
    );

    @Override
    public void parse(@NotNull PsiBuilder builder) {
      parseImportList(builder, b -> IMPORT_PARSING_STOP_LIST.contains(b.getTokenType()));

      final PsiBuilder.Marker rootClass = builder.mark();
      try {
        while (!builder.eof()) {
          PsiBuilder.Marker wrapper = builder.mark();
          IElementType wrapperType = null;

          PsiBuilder.Marker marker = parseImportStatement(builder);
          if (isParsed(marker, builder, IMPORT_PARSED_CONDITION)) {
            wrapperType = JShellElementType.IMPORT_HOLDER;
          }
          else {
            revert(marker);
            marker = getDeclarationParser().parse(builder, DeclarationParser.Context.JSHELL);
            if (isParsed(marker, builder, DECLARATION_PARSED_CONDITION) && !((PsiBuilderImpl)builder).hasErrorsAfter(marker)) {
              wrapper.drop(); // don't need wrapper for top-level declaration
              wrapper = null;
            }
            else {
              revert(marker);
              marker = getStatementParser().parseStatement(builder);
              if (marker != null && !((PsiBuilderImpl)builder).hasErrorsAfter(marker)) {
                wrapperType = JShellElementType.STATEMENTS_HOLDER;
              }
              else {
                revert(marker);
                marker = getExpressionParser().parse(builder);
                wrapperType = marker != null ? JShellElementType.STATEMENTS_HOLDER : null;
              }
            }
          }

          if (marker == null) {
            wrapper.drop();
            break;
          }

          if (wrapper != null) {
            wrapper.done(wrapperType);
          }
        }

        if (!builder.eof()) {
          builder.mark().error(JavaPsiBundle.message("unexpected.token"));
          while (!builder.eof()) {
            builder.advanceLexer();
          }
        }
      }
      finally {
        rootClass.done(JShellElementType.ROOT_CLASS);
      }
    }
  };

  private static boolean isParsed(@Nullable PsiBuilder.Marker parsedMarker, PsiBuilder builder, final Predicate<? super IElementType> cond) {
    if (parsedMarker == null) {
      return false;
    }
    final LighterASTNode lastDone = builder.getLatestDoneMarker();
    if (lastDone == null) {
      return false;
    }
    return cond.test(lastDone.getTokenType());
  }

  private static void revert(PsiBuilder.Marker parsedMarker) {
    if (parsedMarker != null) {
      parsedMarker.rollbackTo();
    }
  }

  @Override
  public @NotNull FileParser getFileParser() {
    return myJShellFileParser;
  }
}
