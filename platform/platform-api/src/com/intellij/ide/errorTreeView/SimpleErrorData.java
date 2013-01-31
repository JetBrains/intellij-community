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
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class SimpleErrorData {
  private final ErrorTreeElementKind myKind;
  private final String[] myMessages;
  private final VirtualFile myVf;

  public SimpleErrorData(@NotNull ErrorTreeElementKind kind, String[] messages, VirtualFile vf) {
    myKind = kind;
    myMessages = messages;
    myVf = vf;
  }

  @NotNull
  public ErrorTreeElementKind getKind() {
    return myKind;
  }

  public String[] getMessages() {
    return myMessages;
  }

  public VirtualFile getVf() {
    return myVf;
  }
}
