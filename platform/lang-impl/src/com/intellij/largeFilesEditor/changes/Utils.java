// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import com.intellij.openapi.editor.ex.DocumentEx;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("WeakerAccess")
public class Utils {


  public static void exchangeStringsInDocument(int offset,
                                               @NotNull CharSequence oldString,
                                               @NotNull CharSequence newString,
                                               @NotNull DocumentEx document) {

    document.replaceString(offset, offset + oldString.length(), newString);
  }

  public static void exchangeStringsInText(int offset,
                                           @NotNull String oldString,
                                           @NotNull String newString,
                                           @NotNull StringBuilder text) {

    text.replace(offset, offset + oldString.length(), newString);
  }
}
