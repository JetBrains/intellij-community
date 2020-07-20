// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.impl.HashBangFileTypeDetector;

public class JavaFileTypeDetector extends HashBangFileTypeDetector {
    public JavaFileTypeDetector() {
      super(JavaFileType.INSTANCE, "java ");
    }
  }