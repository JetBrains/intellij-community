// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.ui.components.panels.NonOpaquePanel;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;

/**
 * Component wrapper for service view descriptor with complex content components,
 * which are not yet supported in CWM.
 */
@ApiStatus.Internal
public class ServiceViewComponentWrapper extends NonOpaquePanel {
  public ServiceViewComponentWrapper(JComponent component) {
    super(new BorderLayout());
    add(component, BorderLayout.CENTER);
  }
}
