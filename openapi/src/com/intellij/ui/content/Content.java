package com.intellij.ui.content;

import com.intellij.openapi.Disposeable;
import com.intellij.openapi.util.UserDataHolder;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public interface Content extends UserDataHolder {
  String PROP_DISPLAY_NAME = "displayName";
  String PROP_ICON = "icon";
  String PROP_DESCRIPTION = "description";
  String PROP_COMPONENT = "component";

  JComponent getComponent();

  void setComponent(JComponent component);

  void setIcon(Icon icon);

  Icon getIcon();

  void setDisplayName(String displayName);

  String getDisplayName();

  Disposeable getDisposer();

  /**
   * @param disposer a Disposeable object whoes dispose() method will be invoken upon this content release.
   */
  void setDisposer(Disposeable disposer);

  String getDescription();

  void setDescription(String description);

  void addPropertyChangeListener(PropertyChangeListener l);

  void removePropertyChangeListener(PropertyChangeListener l);

  ContentManager getManager();

  boolean isSelected();

  void release();

  //TODO[anton,vova] investigate
  boolean isValid();
  boolean isPinned();

  void setPinned(boolean locked);
  boolean isPinnable();
}
