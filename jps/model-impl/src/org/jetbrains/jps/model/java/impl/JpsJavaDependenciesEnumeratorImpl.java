package org.jetbrains.jps.model.java.impl;

import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.impl.JpsDependenciesEnumeratorBase;

import java.util.Collection;

/**
 * @author nik
 */
public class JpsJavaDependenciesEnumeratorImpl extends JpsDependenciesEnumeratorBase<JpsJavaDependenciesEnumeratorImpl> implements JpsJavaDependenciesEnumerator {
  private boolean myProductionOnly;
  private boolean myRuntimeOnly;
  private boolean myCompileOnly;
  private boolean myExportedOnly;
  private boolean myRecursivelyExportedOnly;
  private JpsJavaClasspathKind myClasspathKind;

  public JpsJavaDependenciesEnumeratorImpl(Collection<JpsModule> rootModules) {
    super(rootModules);
  }

  @Override
  public JpsJavaDependenciesEnumerator productionOnly() {
    myProductionOnly = true;
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator compileOnly() {
    myCompileOnly = true;
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator runtimeOnly() {
    myRuntimeOnly = true;
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator exportedOnly() {
    if (myRecursively) {
      myRecursivelyExportedOnly = true;
    }
    else {
      myExportedOnly = true;
    }
    return this;
  }

  @Override
  public JpsJavaDependenciesEnumerator includedIn(JpsJavaClasspathKind classpathKind) {
    myClasspathKind = classpathKind;
    return this;
  }

  @Override
  public JpsJavaDependenciesRootsEnumerator classes() {
    return new JpsJavaDependenciesRootsEnumeratorImpl(this, JpsOrderRootType.COMPILED);
  }

  @Override
  public JpsJavaDependenciesRootsEnumerator sources() {
    return new JpsJavaDependenciesRootsEnumeratorImpl(this, JpsOrderRootType.SOURCES);
  }

  @Override
  protected JpsJavaDependenciesEnumeratorImpl self() {
    return this;
  }

  @Override
  protected boolean shouldProcess(JpsModule module, JpsDependencyElement element) {
    boolean exported = false;
    JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(element);
    if (extension != null) {
      exported = extension.isExported();
      JpsJavaDependencyScope scope = extension.getScope();
      if (myCompileOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE) && !scope.isIncludedIn(JpsJavaClasspathKind.TEST_COMPILE)
        || myRuntimeOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) && !scope.isIncludedIn(JpsJavaClasspathKind.TEST_RUNTIME)
        || myClasspathKind != null && !scope.isIncludedIn(myClasspathKind)) {
        return false;
      }
      if (myProductionOnly) {
        if (!scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE) && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
            || myCompileOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE)
            || myRuntimeOnly && !scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
          return false;
        }
      }
    }
    if (!exported) {
      if (myExportedOnly) return false;
      if (myRecursivelyExportedOnly && !isEnumerationRootModule(module)) return false;
    }
    return true;
  }

  public boolean isProductionOnly() {
    return myProductionOnly || myClasspathKind == JpsJavaClasspathKind.PRODUCTION_RUNTIME || myClasspathKind == JpsJavaClasspathKind.PRODUCTION_COMPILE;
  }
}
