package org.jetbrains.jps.model.java.impl;

import com.intellij.util.Consumer;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependenciesRootsEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.module.impl.JpsDependenciesRootsEnumeratorBase;

/**
 * @author nik
 */
public class JpsJavaDependenciesRootsEnumeratorImpl extends JpsDependenciesRootsEnumeratorBase<JpsJavaDependenciesEnumeratorImpl> implements JpsJavaDependenciesRootsEnumerator {
  private boolean myWithoutSelfModuleOutput;

  public JpsJavaDependenciesRootsEnumeratorImpl(JpsJavaDependenciesEnumeratorImpl dependenciesEnumerator, JpsOrderRootType rootType) {
    super(dependenciesEnumerator, rootType);
  }

  @Override
  public JpsJavaDependenciesRootsEnumerator withoutSelfModuleOutput() {
    myWithoutSelfModuleOutput = true;
    return this;
  }

  protected boolean processModuleRootUrls(JpsModule module, JpsDependencyElement dependencyElement, Consumer<String> urlConsumer) {
    boolean includeProduction, includeTests;
    if (dependencyElement instanceof JpsModuleDependency) {
      boolean productionOnTests = myDependenciesEnumerator.isProductionOnTests(dependencyElement);
      includeProduction = !productionOnTests;
      includeTests = !myDependenciesEnumerator.isProductionOnly() && myDependenciesEnumerator.shouldIncludeTestsFromDependentModulesToTestClasspath()
                     || productionOnTests;
    }
    else {
      includeProduction = true;
      includeTests = !myDependenciesEnumerator.isProductionOnly();
    }

    if (myRootType == JpsOrderRootType.SOURCES) {
      for (JpsModuleSourceRoot root : module.getSourceRoots()) {
        JpsModuleSourceRootType<?> type = root.getRootType();
        if (type.equals(JavaSourceRootType.SOURCE) && includeProduction || type.equals(JavaSourceRootType.TEST_SOURCE) && includeTests) {
          urlConsumer.consume(root.getUrl());
        }
      }
    }
    else if (myRootType == JpsOrderRootType.COMPILED) {
      JpsJavaExtensionService extensionService = JpsJavaExtensionService.getInstance();
      if (myWithoutSelfModuleOutput && myDependenciesEnumerator.isEnumerationRootModule(module)) {
        if (includeProduction && includeTests) {
          String url = extensionService.getOutputUrl(module, false);
          if (url != null) {
            urlConsumer.consume(url);
          }
        }
      }
      else {
        String outputUrl = extensionService.getOutputUrl(module, false);
        if (includeTests) {
          String testsOutputUrl = extensionService.getOutputUrl(module, true);
          if (testsOutputUrl != null && !testsOutputUrl.equals(outputUrl)) {
            urlConsumer.consume(testsOutputUrl);
          }
        }
        if (includeProduction && outputUrl != null) {
          urlConsumer.consume(outputUrl);
        }
      }
    }
    return true;
  }
}
