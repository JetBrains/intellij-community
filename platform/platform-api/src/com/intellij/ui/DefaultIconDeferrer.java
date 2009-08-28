/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.util.Function;

import javax.swing.*;

public class DefaultIconDeferrer extends IconDeferrer {
  public <T> Icon defer(final Icon base, final T param, final Function<T, Icon> f) {
    return f.fun(param);
  }
}