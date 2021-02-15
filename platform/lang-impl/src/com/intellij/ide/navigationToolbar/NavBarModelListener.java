// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar;

import com.intellij.util.messages.Topic;

public interface NavBarModelListener {
  @Topic.ProjectLevel
  Topic<NavBarModelListener> NAV_BAR = new Topic<>(NavBarModelListener.class, Topic.BroadcastDirection.NONE);

  void modelChanged();
  void selectionChanged();
}
