package org.jetbrains.jps.model.module.impl;

import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.*;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author nik
 */
public abstract class JpsDependenciesRootsEnumeratorBase<E extends JpsDependenciesEnumeratorBase<?>> implements JpsDependenciesRootsEnumerator {
  protected final JpsOrderRootType myRootType;
  protected final E myDependenciesEnumerator;

  public JpsDependenciesRootsEnumeratorBase(E dependenciesEnumerator, JpsOrderRootType rootType) {
    myDependenciesEnumerator = dependenciesEnumerator;
    myRootType = rootType;
  }

  @Override
  public Collection<String> getUrls() {
    Set<String> urls = new LinkedHashSet<String>();
    processUrls(new CollectConsumer<String>(urls));
    return urls;
  }

  @Override
  public Collection<File> getRoots() {
    final Set<File> files = new LinkedHashSet<File>();
    processUrls(new Consumer<String>() {
      @Override
      public void consume(String url) {
        files.add(JpsPathUtil.urlToFile(url));
      }
    });
    return files;
  }

  private void processUrls(final Consumer<String> urlConsumer) {
    myDependenciesEnumerator.processDependencies(new Processor<JpsDependencyElement>() {
      @Override
      public boolean process(JpsDependencyElement dependencyElement) {
        if (dependencyElement instanceof JpsModuleSourceDependency) {
          processModuleRootUrls(dependencyElement.getContainingModule(), urlConsumer);
        }
        else if (dependencyElement instanceof JpsModuleDependency) {
          JpsModule dep = ((JpsModuleDependency)dependencyElement).getModule();
          if (dep != null) {
            processModuleRootUrls(dep, urlConsumer);
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
      }
    });
  }

  private boolean processLibraryRootUrls(JpsLibrary library, Consumer<String> urlConsumer) {
    for (String url : library.getRootUrls(myRootType)) {
      urlConsumer.consume(url);
    }
    return true;
  }

  protected abstract boolean processModuleRootUrls(JpsModule module, Consumer<String> urlConsumer);
}
