// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsSafe;

public interface TextAccessor {
  void setText(String text);

  @NlsSafe String getText();
}
