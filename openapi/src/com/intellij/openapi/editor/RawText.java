/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2006
 * Time: 9:31:06 PM
 */
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

public class RawText implements Cloneable, Serializable {
  public static final @NonNls DataFlavor FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + RawText.class.getName(),
                                                                 "Raw Text");
  public String rawText;

  public RawText(final String rawText) {
    this.rawText = rawText;
  }

  public Object clone() {
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException e){
      throw new RuntimeException();
    }
  }
}