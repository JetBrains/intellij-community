package com.intellij.ide.hierarchy;

import com.intellij.ui.content.Content;

import javax.swing.*;

/**
 * Base class for components which can be displayed in the Hierarchy toolwindow.
 *
 * @author yole
 */
public interface HierarchyBrowser {
  /**
   * Returns the UI component to be displayed in the toolwindow.
   *
   * @return the component to show.
   */
  JComponent getComponent();

  /**
   * Notifies the browser that it's being displayed in the specified content.
   *
   * @param content the content in which the browser is displayed.
   */
  void setContent(final Content content);
}
