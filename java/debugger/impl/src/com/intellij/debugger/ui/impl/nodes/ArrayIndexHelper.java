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

/*
 * Class ArrayIndexHelper
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.nodes;

import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.ArrayReference;

public class ArrayIndexHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.nodes.ArrayIndexHelper");
  private final ArrayReference myArray;
  private final ArrayRenderer myRenderer;

  public ArrayIndexHelper(ArrayReference array, ArrayRenderer renderer) {
    myRenderer = renderer;
    myArray = array;
  }

  /**
   * @return normalized start index or -1 if this is not an array or array's length == 0
   */
  public int getStartIndex() {
    if (myArray.length() == 0) return -1;
    return myRenderer.START_INDEX;
  }

  /**
   * @return normalized end index or -1 if this is not an array or array's length == 0
   */

  public int getEndIndex() {
    return Math.min(myArray.length() - 1, myRenderer.END_INDEX);
  }

  public ArrayRenderer newRenderer(int startIdx, int endIdx) {
    ArrayRenderer result = myRenderer.clone();
    result.START_INDEX = startIdx < myArray.length() ? startIdx : 0;
    result.END_INDEX   = startIdx <= endIdx ? endIdx : startIdx;
    return result;
  }
}