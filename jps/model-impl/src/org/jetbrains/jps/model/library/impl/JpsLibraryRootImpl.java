package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;

/**
 * @author nik
 */
public class JpsLibraryRootImpl extends JpsElementBase<JpsLibraryRootImpl> implements JpsLibraryRoot {
  private final String myUrl;
  private final JpsOrderRootType myRootType;
  private final InclusionOptions myOptions;

  public JpsLibraryRootImpl(@NotNull String url, @NotNull JpsOrderRootType rootType, @NotNull InclusionOptions options) {
    myUrl = url;
    myRootType = rootType;
    myOptions = options;
  }

  public JpsLibraryRootImpl(JpsLibraryRootImpl original) {
    myUrl = original.myUrl;
    myRootType = original.myRootType;
    myOptions = original.myOptions;
  }

  @NotNull
  @Override
  public JpsOrderRootType getRootType() {
    return myRootType;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  @Override
  public InclusionOptions getInclusionOptions() {
    return myOptions;
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
