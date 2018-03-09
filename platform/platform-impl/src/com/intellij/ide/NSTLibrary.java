// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.sun.jna.Library;

public interface NSTLibrary extends Library {
  void registerItem(String uid, String type, String text, TBItemCallback action);
}