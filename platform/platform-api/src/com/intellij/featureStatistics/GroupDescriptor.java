// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

public class GroupDescriptor {
  private String myId;
  private @Nls String myDisplayName;
  protected static final @NonNls String ID_ATTR = "id";
  private static final @NonNls String GROUP_PREFIX = "group.";

  @ApiStatus.Internal
  public GroupDescriptor() {
  }

  public GroupDescriptor(String id, @Nls String displayName) {
    myId = id;
    myDisplayName = displayName;
  }

  public void readExternal(Element element) {
    myId = element.getAttributeValue(ID_ATTR);
  }

  public String getId() {
    return myId;
  }

  public @Nls String getDisplayName() {
    if (myDisplayName == null) {
      myDisplayName = FeatureStatisticsBundle.message(GROUP_PREFIX + myId);
    }
    return myDisplayName;
  }
}