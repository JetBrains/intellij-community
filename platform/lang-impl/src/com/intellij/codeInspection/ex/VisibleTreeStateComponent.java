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

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;

import java.util.HashMap;
import java.util.Map;

public class VisibleTreeStateComponent {
  @Property(surroundWithTag = false)
  @MapAnnotation(surroundWithTag=false, surroundKeyWithTag = false, surroundValueWithTag = false)
  public Map<String, VisibleTreeState> myProfileNameToState = new HashMap<String, VisibleTreeState>();

  public void copyFrom(VisibleTreeStateComponent state) {
    myProfileNameToState.clear();
    myProfileNameToState.putAll(state.myProfileNameToState);
  }

  public VisibleTreeState getVisibleTreeState(InspectionProfile profile) {
    VisibleTreeState state = myProfileNameToState.get(profile.getName());
    if (state == null) {
      state = new VisibleTreeState();
      myProfileNameToState.put(profile.getName(), state);
    }
    return state;
  }
}
