/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * User: lex
 * Date: Nov 18, 2003
 * Time: 2:23:38 PM
 */
public class HotSwapFile {
  @NotNull
  final File file;

  public HotSwapFile(@NotNull File file) {
    this.file = file;
  }
}
