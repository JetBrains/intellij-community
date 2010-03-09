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
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

/**
 * @author yole
 */
public class ReferenceTransferableData implements TextBlockTransferableData, Cloneable, Serializable {
  private final ReferenceData[] myReferenceDatas;

  public ReferenceTransferableData(final ReferenceData[] referenceDatas) {
    myReferenceDatas = referenceDatas;
  }

  public DataFlavor getFlavor() {
    return ReferenceData.getDataFlavor();
  }

  public int getOffsetCount() {
    return myReferenceDatas.length * 2;
  }

  public int getOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      offsets[index++] = data.startOffset;
      offsets[index++] = data.endOffset;
    }
    return index;
  }

  public int setOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      data.startOffset = offsets[index++];
      data.endOffset = offsets[index++];
    }
    return index;
  }

  public ReferenceTransferableData clone() {
    ReferenceData[] newReferenceData = new ReferenceData[myReferenceDatas.length];
    for (int i = 0; i < myReferenceDatas.length; i++) {
      newReferenceData[i] = (ReferenceData)myReferenceDatas[i].clone();
    }
    return new ReferenceTransferableData(newReferenceData);
  }

  public ReferenceData[] getData() {
    return myReferenceDatas;
  }

  public static class ReferenceData implements Cloneable, Serializable {
    public static @NonNls DataFlavor ourFlavor;

    public int startOffset;
    public int endOffset;
    public final String qClassName;
    public final String staticMemberName;

    public ReferenceData(int startOffset, int endOffset, String qClassName, String staticMemberDescriptor) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.qClassName = qClassName;
      this.staticMemberName = staticMemberDescriptor;
    }

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
        ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + ReferenceData.class.getName(), "ReferenceData");
      }
      catch (NoClassDefFoundError e) {
        return null;
      }
      return ourFlavor;
    }
  }
}
