// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.HashMap;
import java.util.Map;

public interface DnDNativeTarget extends DnDTarget {
  Logger LOG = Logger.getInstance(DnDNativeTarget.class);

  String EVENT_KEY = "DnDEvent";

  class EventInfo {
    DataFlavor[] myFlavors;
    Transferable myTransferable;

    private final Map<DataFlavor, String> myTexts = new HashMap<>();

    EventInfo(final DataFlavor[] flavors, final Transferable transferable) {
      myFlavors = flavors;
      myTransferable = transferable;
    }

    public DataFlavor[] getFlavors() {
      return myFlavors;
    }

    @Nullable
    public String getTextForFlavor(DataFlavor flavor) {
      if (myTexts.containsKey(flavor)) {
        return myTexts.get(flavor);
      }

      try {
        String text = StreamUtil.readText(flavor.getReaderForText(myTransferable));
        myTexts.put(flavor, text);
        return text;
      }
      catch (Exception e) {
        myTexts.put(flavor, null);
        return null;
      }
    }

    public Transferable getTransferable() {
      return myTransferable;
    }
  }
}
