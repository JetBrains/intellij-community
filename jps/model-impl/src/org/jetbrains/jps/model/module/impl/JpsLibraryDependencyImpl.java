package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.module.JpsLibraryDependency;

/**
 * @author nik
 */
public class JpsLibraryDependencyImpl extends JpsDependencyElementBase<JpsLibraryDependencyImpl> implements JpsLibraryDependency {
  public static final JpsElementKind<JpsLibraryReference> LIBRARY_REFERENCE_KIND = JpsElementKindBase.create("library reference");

  public JpsLibraryDependencyImpl(final JpsLibraryReference reference) {
    super();
    myContainer.setChild(LIBRARY_REFERENCE_KIND, reference);
  }

  public JpsLibraryDependencyImpl(JpsLibraryDependencyImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsLibraryReference getLibraryReference() {
    return myContainer.getChild(LIBRARY_REFERENCE_KIND);
  }

  @Override
  public JpsLibrary getLibrary() {
    return getLibraryReference().resolve();
  }

  @NotNull
  @Override
  public JpsLibraryDependencyImpl createCopy() {
    return new JpsLibraryDependencyImpl(this);
  }
}
