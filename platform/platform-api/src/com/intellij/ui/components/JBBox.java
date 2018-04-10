/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;

/**
 * Custom class overriding the default {@link Box} behavior for accessibility
 * purposes.
 *
 * In general, a {@link Box} is logically closer to a {@link JPanel} than a
 * {@link Filler} in terms of accessibility, because a {@link Box} is meant
 * to contain and layout sub-components, so we return the
 * {@link AccessibleRole#PANEL} role.
 *
 * Specifically, returning the {@link AccessibleRole#FILLER} makes some
 * screen readers announce "filler" whenever the focus change to one of
 * the descendants. This is not an optimal user-experience.
 *
 * By using the "panel" role, we rely on the fact screen readers have
 * special cased components with the "panel" role to skip announcing them,
 * as it is very common for applications to use panels as generic
 * containers without meaningful information for visually impaired users.
 */
public class JBBox extends Box {
  public JBBox(int axis) {
    super(axis);
  }

  /**
   * Use this method in place of {@link Box#createVerticalBox}
   */
  public static JBBox createVerticalBox() {
    return new JBBox(BoxLayout.Y_AXIS);
  }

  /**
   * Use this method in place of {@link Box#createHorizontalBox}
   */
  public static JBBox createHorizontalBox() {
    return new JBBox(BoxLayout.X_AXIS);
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      return new AccessibleJBBox();
    }
    return accessibleContext;
  }

  protected class AccessibleJBBox extends AccessibleBox {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PANEL;
    }
  }
}

