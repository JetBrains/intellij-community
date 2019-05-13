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
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

/**
 * For virtual files containing meta information in the path. Like,
 * {@code x=3746374;y=738495;size=45\id=6729304\id=34343\id=656543}
 * To wrap such a path into compact form implement getPresentablePath and it
 * will be used instead of {@code VirtualFile.getPath()}
 *
 * @author Konstantin Bulenkov
 * @see VirtualFile#getPath() 
 */
public interface VirtualFilePathWrapper {
  @NotNull
  String getPresentablePath();
  
  boolean enforcePresentableName();
}
