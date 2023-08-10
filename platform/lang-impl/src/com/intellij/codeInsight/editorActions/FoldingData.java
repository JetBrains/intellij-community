/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

public class FoldingData implements Cloneable, Serializable {
  private static @NonNls DataFlavor ourFlavor;

  public int startOffset;
  public int endOffset;
  public final boolean isExpanded;
  public final String placeholderText;

  public FoldingData(int startOffset,
                     int endOffset,
                     boolean expanded,
                     String placeholderText){
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    isExpanded = expanded;
    this.placeholderText = placeholderText;
  }

  @Override
  public Object clone() {
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException e){
      throw new RuntimeException();
    }
  }

  public static DataFlavor getDataFlavor() {
    if (ourFlavor != null) {
      return ourFlavor;
    }
    try {
      ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + FoldingData.class.getName(), "FoldingData", FoldingData.class.getClassLoader());
    }
    catch (NoClassDefFoundError | IllegalArgumentException | ClassNotFoundException e) {
      return null;
    }
    return ourFlavor;
  }
}
