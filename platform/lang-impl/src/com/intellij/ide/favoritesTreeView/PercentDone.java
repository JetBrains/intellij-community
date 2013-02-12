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
package com.intellij.ide.favoritesTreeView;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/1/12
 * Time: 1:53 PM
 */
public enum PercentDone {
  _0(0), _05(5), _20(20), _50(50), _70(70), _100(100);

  private final int myPercent;

  private PercentDone(int percent) {
    myPercent = percent;
  }

  public int getPercent() {
    return myPercent;
  }
}
