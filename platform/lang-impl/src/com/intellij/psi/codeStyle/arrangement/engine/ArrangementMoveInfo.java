/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.engine;

public class ArrangementMoveInfo {

  private final int myOldStart;
  private final int myOldEnd;
  private final int myNewStart;

  public ArrangementMoveInfo(int oldStart, int oldEnd, int newStart) {
    myOldStart = oldStart;
    myOldEnd = oldEnd;
    myNewStart = newStart;
  }

  public int getOldStart() {
    return myOldStart;
  }

  public int getOldEnd() {
    return myOldEnd;
  }

  public int getNewStart() {
    return myNewStart;
  }

  @Override
  public String toString() {
    return String.format("range [%d; %d) to offset %d", myOldStart, myOldEnd, myNewStart);
  }
}
