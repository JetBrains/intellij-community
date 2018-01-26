/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author peter
 */
public abstract class CommentMacro extends MacroBase {
  private final Function<Commenter, String> myCommenterFunction;

  protected CommentMacro(String name, String description, Function<Commenter, String> commenterFunction) {
    super(name, description);
    myCommenterFunction = commenterFunction;
  }

  @Nullable
  @Override
  protected Result calculateResult(@NotNull Expression[] params, ExpressionContext context, boolean quick) {
    Editor editor = context.getEditor();
    Language language = editor == null ? null : PsiUtilBase.getLanguageInEditor(editor, context.getProject());
    Commenter commenter = language == null ? null : LanguageCommenters.INSTANCE.forLanguage(language);
    String lineCommentPrefix = commenter == null ? null : myCommenterFunction.apply(commenter);
    return lineCommentPrefix == null ? null : new TextResult(lineCommentPrefix);
  }

  public static class LineCommentStart extends CommentMacro {
    public LineCommentStart() {
      super("lineCommentStart", "Line comment start characters for the current language", Commenter::getLineCommentPrefix);
    }
  }

  public static class BlockCommentStart extends CommentMacro {
    public BlockCommentStart() {
      super("blockCommentStart", "Block comment start characters for the current language", Commenter::getBlockCommentPrefix);
    }
  }

  public static class BlockCommentEnd extends CommentMacro {
    public BlockCommentEnd() {
      super("blockCommentEnd", "Block comment end characters for the current language", Commenter::getBlockCommentSuffix);
    }
  }

  public static class AnyCommentStart extends CommentMacro {
    public AnyCommentStart() {
      super("commentStart", "Comment start characters for the current language, preferring line comment, if it exists",
            commenter -> {
              String line = commenter.getLineCommentPrefix();
              return StringUtil.isNotEmpty(line) ? line : commenter.getBlockCommentPrefix();
            });
    }
  }
  public static class AnyCommentEnd extends CommentMacro {
    public AnyCommentEnd() {
      super("commentEnd", "Comment end characters for the current language, empty if the language has line comments",
            commenter -> {
              String line = commenter.getLineCommentPrefix();
              return StringUtil.isNotEmpty(line) ? "" : commenter.getBlockCommentSuffix();
            });
    }
  }

}
