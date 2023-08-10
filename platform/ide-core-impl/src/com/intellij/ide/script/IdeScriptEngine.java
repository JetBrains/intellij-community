// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.script;

import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.io.Writer;
import java.util.List;

public interface IdeScriptEngine {
  Object getBinding(@NotNull String name);

  void setBinding(@NotNull String name, Object value);

  @NotNull
  Writer getStdOut();

  void setStdOut(@NotNull Writer writer);

  @NotNull
  Writer getStdErr();

  void setStdErr(@NotNull Writer writer);

  @NotNull
  Reader getStdIn();

  void setStdIn(@NotNull Reader reader);

  @NotNull
  String getLanguage();

  @NotNull
  List<String> getFileExtensions();

  Object eval(@NotNull String script) throws IdeScriptException;
}
