package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
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

  public JpsLibraryRootImpl(@NotNull String url,
                            @NotNull JpsLibraryRootType rootType) {
    myUrl = url;
    myRootType = rootType;
  }

  public JpsLibraryRootImpl(JpsLibraryRootImpl original) {
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
  public JpsLibraryRootImpl createCopy() {
    return new JpsLibraryRootImpl(this);
  }

  public void applyChanges(@NotNull JpsLibraryRootImpl modified) {
  }

  @Override
  @NotNull
  public JpsLibrary getLibrary() {
    return (JpsLibrary)myParent.getParent();
  }
}
