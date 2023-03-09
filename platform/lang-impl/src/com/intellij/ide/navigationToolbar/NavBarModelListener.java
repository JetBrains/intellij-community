// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.util.messages.Topic;

/**
 * @deprecated unused in ide.navBar.v2. If you do a change here, please also update v2 implementation
 */
@Deprecated
public interface NavBarModelListener {
  @Topic.ProjectLevel
  Topic<NavBarModelListener> NAV_BAR = new Topic<>(NavBarModelListener.class, Topic.BroadcastDirection.NONE);

  void modelChanged();
  void selectionChanged();
}
