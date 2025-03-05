// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module.impl;

import com.intellij.util.CollectConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public abstract class JpsDependenciesRootsEnumeratorBase<E extends JpsDependenciesEnumeratorBase<?>> implements JpsDependenciesRootsEnumerator {
  protected final JpsOrderRootType myRootType;
  protected final E myDependenciesEnumerator;

  public JpsDependenciesRootsEnumeratorBase(E dependenciesEnumerator, JpsOrderRootType rootType) {
    myDependenciesEnumerator = dependenciesEnumerator;
    myRootType = rootType;
  }

  @Override
  public Collection<String> getUrls() {
    Set<String> urls = new LinkedHashSet<>();
    processUrls(new CollectConsumer<>(urls));
    return urls;
  }

  @Override
  public final Collection<File> getRoots() {
    Set<File> files = new LinkedHashSet<>();
    Consumer<String> urlConsumer = url -> {
      if (!JpsPathUtil.isJrtUrl(url)) {
        files.add(JpsPathUtil.urlToFile(url));
      }
    };
    myDependenciesEnumerator.processDependencies(dependencyElement -> {
      if (dependencyElement instanceof JpsModuleSourceDependency) {
        processModuleRootUrls(dependencyElement.getContainingModule(), dependencyElement, urlConsumer);
      }
      else if (dependencyElement instanceof JpsModuleDependency) {
        JpsModule dep = ((JpsModuleDependency)dependencyElement).getModule();
        if (dep != null) {
          processModuleRootUrls(dep, dependencyElement, urlConsumer);
        }
      }
      else if (dependencyElement instanceof JpsLibraryDependency) {
        JpsLibrary lib = ((JpsLibraryDependency)dependencyElement).getLibrary();
        if (lib != null) {
          files.addAll(lib.getFiles(myRootType));
        }
      }
      else if (dependencyElement instanceof JpsSdkDependency) {
        JpsLibrary lib = ((JpsSdkDependency)dependencyElement).resolveSdk();
        if (lib != null) {
          processLibraryRootUrls(lib, urlConsumer);
        }
      }
      return true;
    });
    return files;
  }

  @Override
  public final Collection<Path> getPaths() {
    Set<Path> files = new LinkedHashSet<>();
    Consumer<String> urlConsumer = url -> {
      if (!JpsPathUtil.isJrtUrl(url)) {
        files.add(Path.of(JpsPathUtil.urlToPath(url)));
      }
    };
    myDependenciesEnumerator.processDependencies(dependencyElement -> {
      if (dependencyElement instanceof JpsModuleSourceDependency) {
        processModuleRootUrls(dependencyElement.getContainingModule(), dependencyElement, urlConsumer);
      }
      else if (dependencyElement instanceof JpsModuleDependency) {
        JpsModule dep = ((JpsModuleDependency)dependencyElement).getModule();
        if (dep != null) {
          processModuleRootUrls(dep, dependencyElement, urlConsumer);
        }
      }
      else if (dependencyElement instanceof JpsLibraryDependency) {
        JpsLibrary lib = ((JpsLibraryDependency)dependencyElement).getLibrary();
        if (lib != null) {
          files.addAll(lib.getPaths(myRootType));
        }
      }
      else if (dependencyElement instanceof JpsSdkDependency) {
        JpsLibrary lib = ((JpsSdkDependency)dependencyElement).resolveSdk();
        if (lib != null) {
          processLibraryRootUrls(lib, urlConsumer);
        }
      }
      return true;
    });
    return files;
  }

  private void processUrls(@NotNull Consumer<? super String> urlConsumer) {
    myDependenciesEnumerator.processDependencies(dependencyElement -> {
      if (dependencyElement instanceof JpsModuleSourceDependency) {
        processModuleRootUrls(dependencyElement.getContainingModule(), dependencyElement, urlConsumer);
      }
      else if (dependencyElement instanceof JpsModuleDependency) {
        JpsModule dep = ((JpsModuleDependency)dependencyElement).getModule();
        if (dep != null) {
          processModuleRootUrls(dep, dependencyElement, urlConsumer);
        }
      }
      else if (dependencyElement instanceof JpsLibraryDependency) {
        JpsLibrary lib = ((JpsLibraryDependency)dependencyElement).getLibrary();
        if (lib != null) {
          processLibraryRootUrls(lib, urlConsumer);
        }
      }
      else if (dependencyElement instanceof JpsSdkDependency) {
        JpsLibrary lib = ((JpsSdkDependency)dependencyElement).resolveSdk();
        if (lib != null) {
          processLibraryRootUrls(lib, urlConsumer);
        }
      }
      return true;
    });
  }

  private void processLibraryRootUrls(JpsLibrary library, Consumer<? super String> urlConsumer) {
    for (String url : library.getRootUrls(myRootType)) {
      urlConsumer.accept(url);
    }
  }

  protected abstract boolean processModuleRootUrls(JpsModule module, JpsDependencyElement dependencyElement, Consumer<? super String> urlConsumer);
}