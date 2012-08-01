package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
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
  public static final JpsElementCollectionKind<JpsDependencyElement> DEPENDENCY_COLLECTION_KIND =
    JpsElementCollectionKind.create(JpsElementKindBase.create("dependency"));

  public JpsDependenciesListImpl() {
    super();
    myContainer.setChild(DEPENDENCY_COLLECTION_KIND);
  }

  private JpsDependenciesListImpl(JpsDependenciesListImpl original) {
    super(original);
  }

  @Override
  @NotNull
  public List<JpsDependencyElement> getDependencies() {
    return myContainer.getChild(DEPENDENCY_COLLECTION_KIND).getElements();
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
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(dependency);
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
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(dependency);
    return dependency;
  }

  @Override
  public void addModuleSourceDependency() {
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(new JpsModuleSourceDependencyImpl());
  }

  @Override
  public void addSdkDependency(@NotNull JpsSdkType<?> sdkType) {
    myContainer.getChild(DEPENDENCY_COLLECTION_KIND).addChild(new JpsSdkDependencyImpl(sdkType));
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
