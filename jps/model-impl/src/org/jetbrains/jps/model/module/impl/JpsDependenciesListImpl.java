package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.*;

import java.util.List;

/**
 * @author nik
 */
public class JpsDependenciesListImpl extends JpsCompositeElementBase<JpsDependenciesListImpl> implements JpsDependenciesList {
  public static final JpsElementCollectionRole<JpsDependencyElement> DEPENDENCY_COLLECTION_ROLE =
    JpsElementCollectionRole.create(JpsElementChildRoleBase.<JpsDependencyElement>create("dependency"));

  public JpsDependenciesListImpl() {
    super();
    myContainer.setChild(DEPENDENCY_COLLECTION_ROLE);
  }

  private JpsDependenciesListImpl(JpsDependenciesListImpl original) {
    super(original);
  }

  @Override
  @NotNull
  public List<JpsDependencyElement> getDependencies() {
    return myContainer.getChild(DEPENDENCY_COLLECTION_ROLE).getElements();
  }

  @Override
  public void clear() {
    myContainer.getChild(DEPENDENCY_COLLECTION_ROLE).removeAllChildren();
  }

  @Override
  @NotNull
  public JpsModuleDependency addModuleDependency(@NotNull JpsModule module) {
    return addModuleDependency(module.createReference());
  }

  @NotNull
  @Override
  public JpsModuleDependency addModuleDependency(@NotNull JpsModuleReference moduleReference) {
    final JpsModuleDependencyImpl dependency = new JpsModuleDependencyImpl(moduleReference);
    myContainer.getChild(DEPENDENCY_COLLECTION_ROLE).addChild(dependency);
    return dependency;
  }

  @Override
  @NotNull
  public JpsLibraryDependency addLibraryDependency(@NotNull JpsLibrary libraryElement) {
    return addLibraryDependency(libraryElement.createReference());
  }

  @NotNull
  @Override
  public JpsLibraryDependency addLibraryDependency(@NotNull JpsLibraryReference libraryReference) {
    final JpsLibraryDependencyImpl dependency = new JpsLibraryDependencyImpl(libraryReference);
    myContainer.getChild(DEPENDENCY_COLLECTION_ROLE).addChild(dependency);
    return dependency;
  }

  @Override
  public void addModuleSourceDependency() {
    myContainer.getChild(DEPENDENCY_COLLECTION_ROLE).addChild(new JpsModuleSourceDependencyImpl());
  }

  @Override
  public void addSdkDependency(@NotNull JpsSdkType<?> sdkType) {
    myContainer.getChild(DEPENDENCY_COLLECTION_ROLE).addChild(new JpsSdkDependencyImpl(sdkType));
  }

  @NotNull
  @Override
  public JpsDependenciesListImpl createCopy() {
    return new JpsDependenciesListImpl(this);
  }

  @Override
  public JpsModuleImpl getParent() {
    return (JpsModuleImpl)super.getParent();
  }
}
