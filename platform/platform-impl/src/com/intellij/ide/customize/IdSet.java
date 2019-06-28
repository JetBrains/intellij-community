// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

class IdSet {
  private static final List<String> BLACK_LIST = Arrays.asList("Support", "support", "Integration", "integration");

  String myTitle;
  String[] myIds;

  IdSet(final PluginGroups pluginGroups, String description) {
    int i = description.indexOf(":");
    if (i > 0) {
      myTitle = description.substring(0, i);
      description = description.substring(i + 1);
    }
    myIds = description.split(",");
    myIds = ContainerUtil.filter(myIds, id -> pluginGroups.findPlugin(id) != null).toArray(new String[]{});

    if (myIds.length > 1 && myTitle == null) {
      throw new IllegalArgumentException("There is no common title for " + myIds.length + " ids: " + description);
    }
    if (myTitle == null && myIds.length>0) {
      //noinspection ConstantConditions
      myTitle = pluginGroups.findPlugin(myIds[0]).getName();
    }
    if (myIds.length == 0 && myTitle != null) {
      myTitle = null;
    }
    if (myTitle != null) {
      for (String skipWord : BLACK_LIST) {
        myTitle = myTitle.replaceAll(skipWord, "");
      }
      myTitle = myTitle.replaceAll("  ", " ").trim();
    }
  }

  @Override
  public String toString() {
    return myTitle + ": " + (myIds != null ? myIds.length : 0);
  }

  @Nullable
  public String getTitle() {
    return myTitle;
  }

  public String[] getIds() {
    return myIds;
  }

}
