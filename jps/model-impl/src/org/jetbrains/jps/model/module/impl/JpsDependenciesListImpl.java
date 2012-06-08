package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsParentElement;
import org.jetbrains.jps.model.impl.JpsCompositeElementBase;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.*;

import java.util.List;

/**
 * @author nik
 */
public class JpsDependenciesListImpl extends JpsCompositeElementBase<JpsDependenciesListImpl> implements JpsDependenciesList {
  public static final JpsElementKind<JpsDependencyElementBase<?>> DEPENDENCY_ELEMENT_KIND = new JpsElementKind<JpsDependencyElementBase<?>>();
  public static final JpsElementCollectionKind<JpsDependencyElementBase<?>> DEPENDENCY_COLLECTION_KIND = new JpsElementCollectionKind<JpsDependencyElementBase<?>>(DEPENDENCY_ELEMENT_KIND);

  public JpsDependenciesListImpl(JpsModel model, JpsEventDispatcher eventDispatcher, JpsModuleImpl parent) {
    super(model, eventDispatcher, parent);
    myContainer.setChild(DEPENDENCY_COLLECTION_KIND);
  }

  public JpsDependenciesListImpl(JpsDependenciesListImpl original, JpsModel model, JpsEventDispatcher dispatcher, JpsParentElement parent) {
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
    return addModuleDependency(module.createReference(this));
  }

  @NotNull
  @Override
  public JpsModuleDependency addModuleDependency(@NotNull JpsModuleReference moduleReference) {
    final JpsModuleDependencyImpl dependency = new JpsModuleDependencyImpl(myModel, getEventDispatcher(), moduleReference, this);
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(dependency);
    return dependency;
  }

  @Override
  @NotNull
  public JpsLibraryDependency addLibraryDependency(@NotNull JpsLibrary libraryElement) {
    return addLibraryDependency(libraryElement.createReference(this));
  }

  @NotNull
  @Override
  public JpsLibraryDependency addLibraryDependency(@NotNull JpsLibraryReference libraryReference) {
    final JpsLibraryDependencyImpl dependency = new JpsLibraryDependencyImpl(myModel, getEventDispatcher(), libraryReference, this);
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(dependency);
    return dependency;
  }

  @Override
  public void addModuleSourceDependency() {
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(new JpsModuleSourceDependency(myModel, getEventDispatcher(), this));
  }

  @Override
  public void addSdkDependency(@NotNull JpsSdkType<?> sdkType) {
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(new JpsSdkDependencyImpl(sdkType, myModel, getEventDispatcher(), this));
  }

  @NotNull
  @Override
  public JpsDependenciesListImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsDependenciesListImpl(this, model, eventDispatcher, parent);
  }

  @Override
  public JpsModuleImpl getParent() {
    return (JpsModuleImpl)super.getParent();
  }
}
