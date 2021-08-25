// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.diagnostic.logging;

import com.intellij.openapi.util.Key;


public class LogFragment {
  private final String myText;
  private final Key myOutputType;

  public LogFragment(final String text, final Key outputType) {
    myText = text;
    myOutputType = outputType;
  }

  public String getText() {
    return myText;
  }

  public Key getOutputType() {
    return myOutputType;
  }
}
