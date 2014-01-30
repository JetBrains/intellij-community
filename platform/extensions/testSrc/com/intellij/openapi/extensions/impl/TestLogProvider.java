/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TestLogProvider extends Extensions.SimpleLogProvider {
  private final List<String> errors = ContainerUtil.newSmartList();

  @Override
  public void error(String message) {
    errors.add(message);
  }

  @Override
  public void error(String message, @NotNull Throwable t) {
    errors.add(message + "\n" + ExceptionUtil.getThrowableText(t));
  }

  @Override
  public void error(@NotNull Throwable t) {
    errors.add(ExceptionUtil.getThrowableText(t));
  }

  @NotNull
  public List<String> errors() {
    List<String> copy = ContainerUtil.newArrayList(errors);
    errors.clear();
    return copy;
  }
}
