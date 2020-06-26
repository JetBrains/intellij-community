// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.compiler;

import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpec;

public final class FormLayoutUtils {
  private FormLayoutUtils() {
  }

  public static String getEncodedRowSpecs(final FormLayout formLayout) {
    StringBuilder result = new StringBuilder();
    for(int i=1; i<=formLayout.getRowCount(); i++) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append(getEncodedSpec(formLayout.getRowSpec(i)));
    }
    return result.toString();
  }

  public static String getEncodedColumnSpecs(final FormLayout formLayout) {
    StringBuilder result = new StringBuilder();
    for(int i=1; i<=formLayout.getColumnCount(); i++) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append(getEncodedSpec(formLayout.getColumnSpec(i)));
    }
    return result.toString();
  }

  public static String getEncodedSpec(final FormSpec formSpec) {
    String result = formSpec.toString();
    while(true) {
      int pos = result.indexOf("dluX");
      if (pos < 0) {
        pos = result.indexOf("dluY");
      }
      if (pos < 0) {
        break;
      }
      result = result.substring(0, pos+3) + result.substring(pos+4);
    }


    return result;
  }
}