package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ColoredListCellRenderer;

import javax.swing.*;

/**
 * @author yole
*/
public class SdkListCellRenderer extends ColoredListCellRenderer {
  private String myNullText = "";
  private boolean myShowHomePath;

  public SdkListCellRenderer() {
  }

  public SdkListCellRenderer(final String nullText) {
    myNullText = nullText;
  }

  public SdkListCellRenderer(String nullText, boolean showHomePath) {
    myNullText = nullText;
    myShowHomePath = showHomePath;
  }

  protected void customizeCellRenderer(final JList list,
                                       final Object value,
                                       final int index,
                                       final boolean selected,
                                       final boolean hasFocus) {
    final Sdk sdk = (Sdk) value;
    if (sdk != null) {
      // icon
      setIcon(sdk.getSdkType().getIcon());
      // text
      append(sdk.getName());
      if (myShowHomePath) {
        append(" (" + FileUtil.toSystemDependentName(sdk.getHomePath()) + ")");
      }
    }
    else {
      append(myNullText);
    }
  }
}
