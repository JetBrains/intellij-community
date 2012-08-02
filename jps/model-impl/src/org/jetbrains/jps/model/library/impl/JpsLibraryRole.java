package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionRole;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryListener;

/**
 * @author nik
 */
public class JpsLibraryRole extends JpsElementChildRoleBase<JpsLibrary> {
  public static final JpsLibraryRole INSTANCE = new JpsLibraryRole();
  public static final JpsElementCollectionRole<JpsLibrary> LIBRARIES_COLLECTION_ROLE = JpsElementCollectionRole.create(INSTANCE);

  private JpsLibraryRole() {
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
