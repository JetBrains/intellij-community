/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.formatting;

public class ExpandableIndent extends IndentImpl {

  private final Object myGroup;
  private boolean myMinGroupOffsetMarker;
  private boolean myEnforceIndent;

  public ExpandableIndent(Type type, Object group) {
    super(type, false, 0, false, true);
    myGroup = group;
    myEnforceIndent = false;
  }

  public boolean isMinGroupOffsetMarker() {
    return myMinGroupOffsetMarker;
  }

  void setMinGroupOffsetMarker(boolean value) {
    myMinGroupOffsetMarker = value;
  }

  public Object getGroup() {
    return myGroup;
  }


  @Override
  public boolean isEnforceIndentToChildren() {
    return myEnforceIndent;
  }

  void setEnforceIndent(boolean value) {
    myEnforceIndent = value;
  }

}
