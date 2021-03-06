// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class IdSet {
  private static final List<@NonNls String> BLACK_LIST = Arrays.asList("Support", "support", "Integration", "integration");

  @Nls String myTitle;
  List<PluginId> myIds;

  IdSet(final PluginGroups pluginGroups, @NonNls String description) {
    int i = description.indexOf(":");
    if (i > 0) {
      myTitle = description.substring(0, i); //NON-NLS
      description = description.substring(i + 1);
    }
    myIds = ContainerUtil.map(description.split(","), PluginId::getId);
    myIds = ContainerUtil.filter(myIds, id -> pluginGroups.findPlugin(id) != null);

    if (myIds.size() > 1 && myTitle == null) {
      throw new IllegalArgumentException("There is no common title for " + myIds.size() + " ids: " + description);
    }
    if (myTitle == null && myIds.size() > 0) {
      //noinspection ConstantConditions
      myTitle = pluginGroups.findPlugin(myIds.get(0)).getName();
    }
    if (myIds.isEmpty() && myTitle != null) {
      myTitle = null;
    }
    if (myTitle != null) {
      for (String skipWord : BLACK_LIST) {
        myTitle = myTitle.replaceAll(skipWord, ""); //NON-NLS
      }
      myTitle = myTitle.replaceAll("  ", " ").trim();
    }
  }

  @Override
  public String toString() {
    return myTitle + ": " + (myIds != null ? myIds.size() : 0);
  }

  @Nullable
  public @Nls String getTitle() {
    return myTitle;
  }

  public List<PluginId> getIds() {
    return myIds;
  }
}
