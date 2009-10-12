/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.featureStatistics;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class GroupDescriptor {
  private String myId;
  private String myDisplayName;
  @NonNls protected static final String ID_ATTR = "id";
  @NonNls private static final String GROUP_PREFIX = "group.";

  GroupDescriptor() {
  }

  public GroupDescriptor(String id, String displayName) {
    myId = id;
    myDisplayName = displayName;
  }

  public void readExternal(Element element) {
    myId = element.getAttributeValue(ID_ATTR);
    myDisplayName = FeatureStatisticsBundle.message(GROUP_PREFIX + myId);
  }

  public String getId() {
    return myId;
  }

  public String getDisplayName() {
    return myDisplayName;
  }
}