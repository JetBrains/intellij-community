/*
 * @author max
 */
package com.intellij.ui;

import javax.swing.*;

public interface DeferredIcon extends Icon {
  Icon evaluate();
}