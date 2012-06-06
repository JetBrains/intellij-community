package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.*;

import java.util.List;

/**
 * @author nik
 */
public class DependenciesListImpl extends JpsCompositeElementBase<DependenciesListImpl> implements DependenciesList {
  public static final JpsElementKind<JpsDependencyElementBase<?>> DEPENDENCY_ELEMENT_KIND = new JpsElementKind<JpsDependencyElementBase<?>>();
  public static final JpsElementCollectionKind<JpsDependencyElementBase<?>> DEPENDENCY_COLLECTION_KIND = new JpsElementCollectionKind<JpsDependencyElementBase<?>>(DEPENDENCY_ELEMENT_KIND);

  public DependenciesListImpl(JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(model, eventDispatcher, parent);
    myContainer.setChild(DEPENDENCY_COLLECTION_KIND);
  }

  public DependenciesListImpl(DependenciesListImpl original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(original, model, dispatcher, parent);
  }

  @Override
  @NotNull
  public List<? extends JpsDependencyElement> getDependencies() {
    return myContainer.getChild(DEPENDENCY_COLLECTION_KIND).getElements();
  }

  @Override
  @NotNull
  public JpsModuleDependency addModuleDependency(@NotNull JpsModule module) {
    final JpsModuleDependencyImpl dependency = new JpsModuleDependencyImpl(myModel, getEventDispatcher(), module, this);
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(dependency);
    return dependency;
  }

  @Override
  @NotNull
  public JpsLibraryDependency addLibraryDependency(@NotNull JpsLibrary libraryElement) {
    JpsLibraryDependencyImpl dependency = new JpsLibraryDependencyImpl(myModel, getEventDispatcher(), libraryElement, this);
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(dependency);
    return dependency;
  }

  @NotNull
  @Override
  public DependenciesListImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new DependenciesListImpl(this, model, eventDispatcher, parent);
  }
}
