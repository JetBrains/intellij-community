package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.library.JpsLibraryRootListener;

/**
 * @author nik
 */
public class JpsLibraryRootKind extends JpsElementKind<JpsLibraryRootImpl> {
  public static final JpsLibraryRootKind INSTANCE = new JpsLibraryRootKind();

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryRootImpl element) {
    dispatcher.getPublisher(JpsLibraryRootListener.class).rootAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryRootImpl element) {
    dispatcher.getPublisher(JpsLibraryRootListener.class).rootRemoved(element);
  }
}
