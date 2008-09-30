/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.Function;

import javax.swing.*;

public abstract class IconDeferrer {
  public static IconDeferrer getInstance() {
    return ServiceManager.getService(IconDeferrer.class);
  }

  public abstract <T> Icon defer(Icon base, T param, Function<T, Icon> f);
}