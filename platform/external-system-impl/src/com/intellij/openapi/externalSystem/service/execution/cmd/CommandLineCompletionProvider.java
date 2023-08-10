// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution.cmd;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.util.TextFieldCompletionProvider;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jetbrains.annotations.NotNull;

public abstract class CommandLineCompletionProvider extends TextFieldCompletionProvider {

  private final Options myOptions;

  public CommandLineCompletionProvider(Options options) {
    super(true);

    myOptions = options;
  }

  @NotNull
  @Override
  protected String getPrefix(@NotNull String currentTextPrefix) {
    ParametersListLexer lexer = new ParametersListLexer(currentTextPrefix);
    while (lexer.nextToken()) {
      if (lexer.getTokenEnd() == currentTextPrefix.length()) {
        return lexer.getCurrentToken();
      }
    }

    return "";
  }

  protected LookupElement createLookupElement(@NotNull Option option, @NotNull String text) {
    LookupElementBuilder res = LookupElementBuilder.create(text);

    if (option.getDescription() != null) {
      return TailTypeDecorator.withTail(res.withTypeText(option.getDescription(), true), TailType.INSERT_SPACE);
    }

    return res;
  }

  protected abstract void addArgumentVariants(@NotNull CompletionResultSet result);

  @Override
  protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
    ParametersListLexer lexer = new ParametersListLexer(text);

    int argCount = 0;

    while (lexer.nextToken()) {
      if (offset < lexer.getTokenStart()) {
        break;
      }

      if (offset <= lexer.getTokenEnd()) {
        if (argCount == 0) {
          if (prefix.startsWith("--")) {
            for (Option option : myOptions.getOptions()) {
              if (option.getLongOpt() != null) {
                result.addElement(createLookupElement(option, "--" + option.getLongOpt()));
              }
            }
          }
          else if (prefix.startsWith("-")) {
            for (Option option : myOptions.getOptions()) {
              if (option.getOpt() != null) {
                result.addElement(createLookupElement(option, "-" + option.getOpt()));
              }
            }
          }
          else {
            addArgumentVariants(result);
          }
        }

        return;
      }

      if (argCount > 0) {
        argCount--;
      }
      else {
        String token = lexer.getCurrentToken();

        if (token.startsWith("-")) {
          Option option = myOptions.getOption(token);
          if (option != null) {
            int optionArgCount = option.getArgs();

            if (optionArgCount == Option.UNLIMITED_VALUES) {
              argCount = Integer.MAX_VALUE;
            }
            else if (optionArgCount != Option.UNINITIALIZED) {
              argCount = optionArgCount;
            }
          }
        }
      }
    }

    if (argCount > 0) {
      return;
    }

    addArgumentVariants(result);
  }

}
