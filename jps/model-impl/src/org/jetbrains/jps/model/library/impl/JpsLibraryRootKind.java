package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsLibraryRootListener;

/**
 * @author nik
 */
public class JpsLibraryRootKind extends JpsElementKindBase<JpsLibraryRoot> {
  public static final JpsLibraryRootKind INSTANCE = new JpsLibraryRootKind();

  public JpsLibraryRootKind() {
    super("library root");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryRoot element) {
    dispatcher.getPublisher(JpsLibraryRootListener.class).rootAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryRoot element) {
    dispatcher.getPublisher(JpsLibraryRootListener.class).rootRemoved(element);
  }
}
