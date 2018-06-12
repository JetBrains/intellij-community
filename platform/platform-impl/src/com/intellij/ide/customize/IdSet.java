/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.customize;

import com.intellij.openapi.util.Condition;
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
    return String.valueOf(myTitle) + ": " + (myIds != null ? myIds.length : 0);
  }

  @Nullable
  public String getTitle() {
    return myTitle;
  }

  public String[] getIds() {
    return myIds;
  }

}
