package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.module.JpsLibraryDependency;

/**
 * @author nik
 */
public class JpsLibraryDependencyImpl extends JpsDependencyElementBase<JpsLibraryDependencyImpl> implements JpsLibraryDependency {
  public static final JpsElementKind<JpsLibraryReference> LIBRARY_REFERENCE_KIND = new JpsElementKind<JpsLibraryReference>();

  public JpsLibraryDependencyImpl(JpsModel model, JpsEventDispatcher eventDispatcher, JpsLibrary library, JpsDependenciesListImpl parent) {
    super(model, eventDispatcher, parent);
    myContainer.setChild(LIBRARY_REFERENCE_KIND, library.createReference(parent));
  }

  public JpsLibraryDependencyImpl(JpsLibraryDependencyImpl original, @NotNull JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
  }

  @NotNull
  @Override
  public JpsLibraryReference getLibraryReference() {
    return myContainer.getChild(LIBRARY_REFERENCE_KIND);
  }

  @NotNull
  @Override
  public JpsLibraryDependencyImpl createCopy(@NotNull JpsModel model,
                                             @NotNull JpsEventDispatcher eventDispatcher,
                                             JpsParentElement parent) {
    return new JpsLibraryDependencyImpl(this, model, eventDispatcher, parent);
  }
}
