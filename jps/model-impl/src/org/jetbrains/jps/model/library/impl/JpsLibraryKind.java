package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.library.JpsLibraryListener;

/**
 * @author nik
 */
public class JpsLibraryKind extends JpsElementKindBase<JpsLibraryImpl> {
  public static final JpsLibraryKind INSTANCE = new JpsLibraryKind();
  public static final JpsElementCollectionKind<JpsLibraryImpl> LIBRARIES_COLLECTION_KIND = new JpsElementCollectionKind<JpsLibraryImpl>(INSTANCE);

  private JpsLibraryKind() {
    super("library");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryImpl element) {
    dispatcher.getPublisher(JpsLibraryListener.class).libraryAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryImpl element) {
    dispatcher.getPublisher(JpsLibraryListener.class).libraryRemoved(element);
  }
}
