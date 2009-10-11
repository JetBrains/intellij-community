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
package com.intellij.openapi.vfs;

import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public interface LocalFileOperationsHandler {
  boolean delete(VirtualFile file) throws IOException;
  boolean move(VirtualFile file, VirtualFile toDir) throws IOException;
  @Nullable
  File copy(VirtualFile file, VirtualFile toDir, final String copyName) throws IOException;
  boolean rename(VirtualFile file, String newName) throws IOException;

  boolean createFile(VirtualFile dir, String name) throws IOException;
  boolean createDirectory(VirtualFile dir, String name) throws IOException;
  void afterDone(final ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker);
}
