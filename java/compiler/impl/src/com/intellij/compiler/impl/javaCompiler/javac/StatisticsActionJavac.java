/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.OutputParser;
import com.intellij.openapi.compiler.CompilerBundle;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 24, 2005
 */
public class StatisticsActionJavac extends JavacParserAction {
  private final String myProgressMessageResourceKey;

  public StatisticsActionJavac(final Matcher matcher, String progressMessageResourceKey) {
    super(matcher);
    myProgressMessageResourceKey = progressMessageResourceKey;
  }

  protected void doExecute(final String line, @Nullable String parsedData, final OutputParser.Callback callback) {
    callback.setProgressText(CompilerBundle.message(myProgressMessageResourceKey));
  }
}
