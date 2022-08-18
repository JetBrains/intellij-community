// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

/**
* @author Denis Fokin
*/
public class ReferenceData implements Cloneable, Serializable {
  public static @NonNls DataFlavor ourFlavor;

  public int startOffset;
  public int endOffset;
  @NlsSafe @NotNull
  public final String qClassName;
  public final String staticMemberName;

  public ReferenceData(int startOffset, int endOffset, @NotNull String qClassName, String staticMemberDescriptor) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.qClassName = qClassName;
    this.staticMemberName = staticMemberDescriptor;
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
      ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + ReferenceData.class.getName(), "ReferenceData", ReferenceData.class.getClassLoader());
    }
    catch (NoClassDefFoundError | IllegalArgumentException | ClassNotFoundException e) {
      return null;
    }
    return ourFlavor;
  }
}
