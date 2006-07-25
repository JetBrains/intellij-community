/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 * User: yole
 * Date: 25.07.2006
 * Time: 18:27:54
 */
package com.intellij.uiDesigner.compiler;

import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpec;

public class FormLayoutUtils {
  public static String getEncodedRowSpecs(final FormLayout formLayout) {
    StringBuffer result = new StringBuffer();
    for(int i=1; i<=formLayout.getRowCount(); i++) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append(getEncodedSpec(formLayout.getRowSpec(i)));
    }
    return result.toString();
  }

  public static String getEncodedColumnSpecs(final FormLayout formLayout) {
    StringBuffer result = new StringBuffer();
    for(int i=1; i<=formLayout.getColumnCount(); i++) {
      if (result.length() > 0) {
        result.append(",");
      }
      result.append(getEncodedSpec(formLayout.getColumnSpec(i)));
    }
    return result.toString();
  }

  public static String getEncodedSpec(final FormSpec formSpec) {
    return formSpec.toString().replace("dluX", "dlu").replace("dluY", "dlu");
  }
}