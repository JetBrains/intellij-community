// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.JavaFileType;

public class JavaFileTypeDetector extends HashBangFileTypeDetector {
  public JavaFileTypeDetector() {
    super(JavaFileType.INSTANCE, "java");
  }
}
