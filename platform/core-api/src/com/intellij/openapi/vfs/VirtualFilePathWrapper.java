// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
