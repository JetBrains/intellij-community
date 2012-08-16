package org.jetbrains.jps.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsModuleRootModificationUtil {
  public static void addDependency(JpsModule module, JpsLibrary library) {
    addDependency(module, library, JpsJavaDependencyScope.COMPILE, false);
  }

  public static void addDependency(JpsModule module, JpsLibrary library, final JpsJavaDependencyScope scope, final boolean exported) {
    setDependencyProperties(module.getDependenciesList().addLibraryDependency(library), scope, exported);
  }

  private static void setDependencyProperties(JpsDependencyElement dependency, JpsJavaDependencyScope scope, boolean exported) {
    JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency);
    extension.setExported(exported);
    extension.setScope(scope);
  }

  public static void setModuleSdk(JpsModule module, @Nullable JpsSdk<JpsDummyElement> sdk) {
    module.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, sdk != null ? sdk.createReference() : null);
  }

  public static void setSdkInherited(JpsModule module) {
    module.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, null);
  }

  public static void addDependency(final JpsModule from, final JpsModule to) {
    addDependency(from, to, JpsJavaDependencyScope.COMPILE, false);
  }

  public static void addDependency(final JpsModule from, final JpsModule to, final JpsJavaDependencyScope scope, final boolean exported) {
    setDependencyProperties(from.getDependenciesList().addModuleDependency(to), scope, exported);
  }
}
