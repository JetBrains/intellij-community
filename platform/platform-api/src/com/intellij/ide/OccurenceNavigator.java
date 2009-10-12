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
package com.intellij.ide;

import com.intellij.pom.Navigatable;

public interface OccurenceNavigator {
  class OccurenceInfo {
    private final Navigatable myNavigateable;
    private final int myOccurenceNumber;
    private final int myOccurencesCount;

    public OccurenceInfo(Navigatable navigateable, int occurenceNumber, int occurencesCount) {
      myNavigateable = navigateable;
      myOccurenceNumber = occurenceNumber;
      myOccurencesCount = occurencesCount;
    }

    private OccurenceInfo(int occurenceNumber, int occurencesCount) {
      this(null, occurenceNumber, occurencesCount);
    }

    public static OccurenceInfo position(int occurenceNumber, int occurencesCount) {
      return new OccurenceInfo(occurenceNumber, occurencesCount);
    }

    public Navigatable getNavigateable() {
      return myNavigateable;
    }

    public int getOccurenceNumber() {
      return myOccurenceNumber;
    }

    public int getOccurencesCount() {
      return myOccurencesCount;
    }
  }

  boolean hasNextOccurence();
  boolean hasPreviousOccurence();
  OccurenceInfo goNextOccurence();
  OccurenceInfo goPreviousOccurence();
  String getNextOccurenceActionName();
  String getPreviousOccurenceActionName();
}
