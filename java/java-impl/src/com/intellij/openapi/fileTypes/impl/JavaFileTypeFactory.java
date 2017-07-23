/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class JavaFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    for (FileType ft : Arrays.asList(JavaClassFileType.INSTANCE, JavaFileType.INSTANCE, JShellFileType.INSTANCE)) {
      consumer.consume(ft, ft.getDefaultExtension());
    }
  }
}
