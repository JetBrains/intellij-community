package org.jetbrains.jps.model.module.impl;

import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public abstract class JpsDependenciesEnumeratorBase<Self extends JpsDependenciesEnumerator> implements JpsDependenciesEnumerator {
  private boolean myWithoutSdk;
  private boolean myWithoutLibraries;
  protected boolean myWithoutDepModules;
  private boolean myWithoutModuleSourceEntries;
  protected boolean myRecursively;
  protected final Collection<JpsModule> myRootModules;

  protected JpsDependenciesEnumeratorBase(Collection<JpsModule> rootModules) {
    myRootModules = rootModules;
  }

  @Override
  public Self withoutLibraries() {
    myWithoutLibraries = true;
    return self();
  }

  @Override
  public Self withoutDepModules() {
    myWithoutDepModules = true;
    return self();
  }

  @Override
  public Self withoutSdk() {
    myWithoutSdk = true;
    return self();
  }

  @Override
  public Self withoutModuleSourceEntries() {
    myWithoutModuleSourceEntries = true;
    return self();
  }

  @Override
  public Self recursively() {
    myRecursively = true;
    return self();
  }

  protected abstract Self self();

  @Override
  public Set<JpsModule> getModules() {
    Set<JpsModule> result = new HashSet<JpsModule>();
    processModules(new CollectConsumer<JpsModule>(result));
    return result;
  }

  public void processModules(final Consumer<JpsModule> consumer) {
    processDependencies(new Processor<JpsDependencyElement>() {
      @Override
      public boolean process(JpsDependencyElement dependencyElement) {
        if (myRecursively && dependencyElement instanceof JpsModuleSourceDependency) {
          consumer.consume(dependencyElement.getContainingModule());
        }
        else if (!myRecursively && dependencyElement instanceof JpsModuleDependency) {
          JpsModule module = ((JpsModuleDependency)dependencyElement).getModule();
          if (module != null) {
            consumer.consume(module);
          }
        }
        return true;
      }
    });
  }

  public boolean processDependencies(Processor<JpsDependencyElement> processor) {
    THashSet<JpsModule> processed = new THashSet<JpsModule>();
    for (JpsModule module : myRootModules) {
      if (!doProcessDependencies(module, processor, processed)) {
        return false;
      }
    }
    return true;
  }

  private boolean doProcessDependencies(JpsModule module, Processor<JpsDependencyElement> processor, Set<JpsModule> processed) {
    if (!processed.add(module)) return true;

    for (JpsDependencyElement element : module.getDependenciesList().getDependencies()) {
      if (myWithoutSdk && element instanceof JpsSdkDependency
       || myWithoutLibraries && element instanceof JpsLibraryDependency
       || myWithoutModuleSourceEntries && element instanceof JpsModuleSourceDependency) continue;
      if (myWithoutDepModules) {
        if (!myRecursively && element instanceof JpsModuleDependency) continue;
        if (element instanceof JpsModuleSourceDependency && !isEnumerationRootModule(module)) continue;
      }
      if (!shouldProcess(module, element)) {
        continue;
      }

      if (element instanceof JpsModuleDependency) {
        if (myRecursively) {
          JpsModule depModule = ((JpsModuleDependency)element).getModule();
          if (depModule != null) {
            doProcessDependencies(depModule, processor, processed);
            continue;
          }
        }
        if (myWithoutDepModules) continue;
      }

      if (!processor.process(element)) {
        return false;
      }
    }

    return true;
  }

  protected boolean shouldProcess(JpsModule module, JpsDependencyElement element) {
    return true;
  }

  public boolean isEnumerationRootModule(JpsModule module) {
    return myRootModules.contains(module);
  }

  @Override
  public Set<JpsLibrary> getLibraries() {
    Set<JpsLibrary> libraries = new HashSet<JpsLibrary>();
    processLibraries(new CollectConsumer<JpsLibrary>(libraries));
    return libraries;
  }

  public void processLibraries(final Consumer<JpsLibrary> consumer) {
    processDependencies(new Processor<JpsDependencyElement>() {
      @Override
      public boolean process(JpsDependencyElement dependencyElement) {
        if (dependencyElement instanceof JpsLibraryDependency) {
          JpsLibrary library = ((JpsLibraryDependency)dependencyElement).getLibrary();
          if (library != null) {
            consumer.consume(library);
          }
        }
        return true;
      }
    });
  }
}
