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
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

public class PopupUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.popup.util.PopupUtil");

  private PopupUtil() {
  }

  @Nullable
  public static Component getOwner(@Nullable Component c) {
    if (c == null) return null;

    final Window wnd = SwingUtilities.getWindowAncestor(c);
    if (wnd instanceof JWindow) {
      final JRootPane root = ((JWindow)wnd).getRootPane();
      final JBPopup popup = (JBPopup)root.getClientProperty(JBPopup.KEY);
      if (popup == null) return c;

      final Component owner = popup.getOwner();
      if (owner == null) return c;

      return getOwner(owner);
    }
    else {
      return c;
    }
  }

  public static void setPopupType(@NotNull final PopupFactory factory, final int type) {
    try {
      final Method method = PopupFactory.class.getDeclaredMethod("setPopupType", int.class);
      method.setAccessible(true);
      method.invoke(factory, type);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  public static int getPopupType(@NotNull final PopupFactory factory) {
    try {
      final Method method = PopupFactory.class.getDeclaredMethod("getPopupType");
      method.setAccessible(true);
      final Object result = method.invoke(factory);
      return result instanceof Integer ? (Integer) result : -1;
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    return -1;
  }

}