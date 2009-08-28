package com.intellij.ui;

import javax.swing.event.ChangeListener;
import java.util.EventListener;

/**
 * @author Roman Chernyatchik
 */
public interface UserActivityProviderComponent extends EventListener {

  void addChangeListener(final ChangeListener changeListener);
  void removeChangeListener(final ChangeListener changeListener);
}
