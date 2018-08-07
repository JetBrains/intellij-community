// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.util.JDOMExternalizable;
import com.sun.jdi.Type;

public interface Renderer extends Cloneable, JDOMExternalizable {
  String getUniqueId();

  /***
   * Checks whether this renderer is applicable to this value
   * @param type
   */
  boolean isApplicable(Type type);

  Renderer clone();
}
