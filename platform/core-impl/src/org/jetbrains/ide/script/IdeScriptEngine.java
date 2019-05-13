/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.ide.script;

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
