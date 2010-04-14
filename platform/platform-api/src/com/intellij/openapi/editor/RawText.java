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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2006
 * Time: 9:31:06 PM
 */
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.Serializable;

public class RawText implements Cloneable, Serializable {
  public static @NonNls DataFlavor ourFlavor;
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

  public static DataFlavor getDataFlavor() {
    if (ourFlavor != null) {
      return ourFlavor;
    }
    try {
      ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + RawText.class.getName(), "Raw Text");
    }
    catch (NoClassDefFoundError e) {
      return null;
    }
    catch (IllegalArgumentException e) {
      return null;
    }
    return ourFlavor;
  }

  @Nullable
  public static RawText fromTransferable(Transferable content) {
    RawText raw = null;
    final DataFlavor flavor = getDataFlavor();
    if (flavor != null) {
      try {
        raw = (RawText)content.getTransferData(flavor);
      }
      catch (UnsupportedFlavorException e) {
        // OK. raw will be null and we'll get plain string
      }
      catch (IOException e) {
        // OK. raw will be null and we'll get plain string
      }
    }
    return raw;
  }
}