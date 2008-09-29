
/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.codeHighlighting;

public interface Pass {
  int UPDATE_FOLDING = 0x01;
  int UPDATE_VISIBLE = 0x02;
  int POPUP_HINTS = 0x04;
  int UPDATE_ALL = 0x08;
  int POST_UPDATE_ALL = 0x10;
  int UPDATE_OVERRIDEN_MARKERS = 0x20;
  int LOCAL_INSPECTIONS = 0x40;
  int EXTERNAL_TOOLS = 0x100;
  int WOLF = 0x200;

  int LAST_PASS = WOLF;
}