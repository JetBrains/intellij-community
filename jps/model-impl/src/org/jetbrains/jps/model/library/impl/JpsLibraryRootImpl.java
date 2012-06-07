package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsElementBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsLibraryRootType;

/**
 * @author nik
 */
public class JpsLibraryRootImpl extends JpsElementBase<JpsLibraryRootImpl> implements JpsLibraryRoot {
  private final String myUrl;
  private final JpsLibraryRootType myRootType;

  public JpsLibraryRootImpl(JpsEventDispatcher eventDispatcher, @NotNull String url, @NotNull JpsLibraryRootType rootType, @NotNull JpsParentElement parent) {
    super(eventDispatcher, parent);
    myUrl = url;
    myRootType = rootType;
  }

  public JpsLibraryRootImpl(JpsLibraryRootImpl original, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, dispatcher, parent);
    myUrl = original.myUrl;
    myRootType = original.myRootType;
  }

  @NotNull
  @Override
  public JpsLibraryRootType getRootType() {
    return myRootType;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public JpsLibraryRootImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsLibraryRootImpl(this, eventDispatcher, parent);
  }

  public void applyChanges(@NotNull JpsLibraryRootImpl modified) {
  }

  @Override
  @NotNull
  public JpsLibrary getLibrary() {
    return (JpsLibrary)myParent;
  }
}
