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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.*;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    consumer.consume(new JavaClassFileType(), "class");

    consumer.consume(JavaFileType.INSTANCE, "java");

    consumer.consume(new WorkspaceFileType(), WorkspaceFileType.DEFAULT_EXTENSION);
    consumer.consume(new ModuleFileType(), ModuleFileType.DEFAULT_EXTENSION);
    consumer.consume(new ProjectFileType(), ProjectFileType.DEFAULT_EXTENSION);
  }

}
