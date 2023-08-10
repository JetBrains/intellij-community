// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import java.io.IOException;

public interface FileTypeNameEnumerator {
  int getFileTypeId(String name) throws IOException;

  String getFileTypeName(int id) throws IOException;
}
