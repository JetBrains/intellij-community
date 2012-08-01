package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryListener;

/**
 * @author nik
 */
public class JpsLibraryKind extends JpsElementKindBase<JpsLibrary> {
  public static final JpsLibraryKind INSTANCE = new JpsLibraryKind();
  public static final JpsElementCollectionKind<JpsLibrary> LIBRARIES_COLLECTION_KIND = JpsElementCollectionKind.create(INSTANCE);

  private JpsLibraryKind() {
    super("library");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibrary element) {
    dispatcher.getPublisher(JpsLibraryListener.class).libraryAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibrary element) {
    dispatcher.getPublisher(JpsLibraryListener.class).libraryRemoved(element);
  }
}
