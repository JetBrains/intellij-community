// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import java.util.EventListener;

public interface MessagePoolListener extends EventListener {
  void newEntryAdded();
  void poolCleared();
  void entryWasRead();
}