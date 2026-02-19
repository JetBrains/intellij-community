// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.ParserAction;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;

/**
 * @author Eugene Zhuravlev
 */
public abstract class JavacParserAction extends ParserAction {
  private final Matcher myMatcher;

  protected JavacParserAction(final Matcher matcher) {
    myMatcher = matcher;
  }

  @Override
  public final boolean execute(@NlsSafe String line, final OutputParser.Callback callback) {
    myMatcher.reset(line);
    if (!myMatcher.matches()) {
      return false;
    }
    final String parsed = myMatcher.groupCount() >= 1 ? myMatcher.group(1).replace(File.separatorChar, '/') : null;
    doExecute(line, parsed, callback);
    return true;
  }

  protected abstract void doExecute(final @NlsSafe String line, @Nullable String parsedData, final OutputParser.Callback callback);

}
