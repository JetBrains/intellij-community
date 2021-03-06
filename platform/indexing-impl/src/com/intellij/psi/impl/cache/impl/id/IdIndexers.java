// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.fileTypes.FileTypeExtension;

/**
 * @author yole
 */
public final class IdIndexers extends FileTypeExtension<IdIndexer> {
  public static final IdIndexers INSTANCE = new IdIndexers();

  private IdIndexers() {
    super("com.intellij.idIndexer");
  }
}
