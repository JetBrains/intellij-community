// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.util.CollectConsumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsDependenciesEnumerator;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleSourceDependency;
import org.jetbrains.jps.model.module.JpsSdkDependency;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public abstract class JpsDependenciesEnumeratorBase<Self extends JpsDependenciesEnumerator> implements JpsDependenciesEnumerator {
  private boolean myWithoutSdk;
  private boolean myWithoutLibraries;
  private boolean myWithoutDepModules;
  private boolean myWithoutModuleSourceEntries;
  protected boolean myRecursively;
  private final Set<JpsModule> myRootModules;
  private Condition<? super JpsDependencyElement> myCondition;

  protected JpsDependenciesEnumeratorBase(Collection<JpsModule> rootModules) {
    myRootModules = Collections.unmodifiableSet(
      rootModules instanceof Set? (Set<JpsModule>)rootModules : new LinkedHashSet<>(rootModules)
    );
  }

  @Override
  public @NotNull Self withoutLibraries() {
    myWithoutLibraries = true;
    return self();
  }

  @Override
  public @NotNull Self withoutDepModules() {
    myWithoutDepModules = true;
    return self();
  }

  @Override
  public @NotNull Self withoutSdk() {
    myWithoutSdk = true;
    return self();
  }

  @Override
  public @NotNull Self withoutModuleSourceEntries() {
    myWithoutModuleSourceEntries = true;
    return self();
  }

  @Override
  public @NotNull Self satisfying(@NotNull Condition<? super JpsDependencyElement> condition) {
    myCondition = condition;
    return self();
  }

  @Override
  public @NotNull Self recursively() {
    myRecursively = true;
    return self();
  }

  protected abstract Self self();

  @Override
  public @NotNull Set<JpsModule> getModules() {
    Set<JpsModule> result = new LinkedHashSet<>();
    forEachModule(new CollectConsumer<>(result));
    return result;
  }

  @Override
  public void forEachModule(@NotNull Consumer<? super JpsModule> consumer) {
    forEachModuleAndLibrary(consumer, EmptyConsumer.getInstance());
  }

  protected boolean shouldProcessDependenciesRecursively() {
    return true;
  }

  public boolean processDependencies(Processor<? super JpsDependencyElement> processor) {
    Set<JpsModule> processed = CollectionFactory.createSmallMemoryFootprintSet();
    for (JpsModule module : myRootModules) {
      if (!doProcessDependencies(module, processor, processed)) {
        return false;
      }
    }
    return true;
  }

  private boolean doProcessDependencies(JpsModule module, Processor<? super JpsDependencyElement> processor, Set<? super JpsModule> processed) {
    if (!processed.add(module)) return true;

    for (JpsDependencyElement element : module.getDependenciesList().getDependencies()) {
      if (myCondition != null && !myCondition.value(element)) continue;

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
        if (myRecursively && shouldProcessDependenciesRecursively()) {
          JpsModule depModule = ((JpsModuleDependency)element).getModule();
          if (depModule != null) {
            if (!doProcessDependencies(depModule, processor, processed)) {
              return false;
            }
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
  public @NotNull Set<JpsLibrary> getLibraries() {
    Set<JpsLibrary> libraries = new LinkedHashSet<>();
    forEachLibrary(new CollectConsumer<>(libraries));
    return libraries;
  }

  @Override
  public void forEachLibrary(@NotNull Consumer<? super JpsLibrary> consumer) {
    forEachModuleAndLibrary(EmptyConsumer.getInstance(), consumer);
  }

  @Override
  public void forEachModuleAndLibrary(@NotNull Consumer<? super JpsModule> moduleConsumer,
                                      @NotNull Consumer<? super JpsLibrary> libraryConsumer) {
    processDependencies(dependencyElement -> {
      if (myRecursively && dependencyElement instanceof JpsModuleSourceDependency) {
        moduleConsumer.accept(dependencyElement.getContainingModule());
      }
      else if ((!myRecursively || !shouldProcessDependenciesRecursively()) && dependencyElement instanceof JpsModuleDependency) {
        JpsModule module = ((JpsModuleDependency)dependencyElement).getModule();
        if (module != null) {
          moduleConsumer.accept(module);
        }
      }
      if (dependencyElement instanceof JpsLibraryDependency) {
        JpsLibrary library = ((JpsLibraryDependency)dependencyElement).getLibrary();
        if (library != null) {
          libraryConsumer.accept(library);
        }
      }
      return true;
    });
  }

  @Override
  public void processModules(final @NotNull com.intellij.util.Consumer<? super JpsModule> consumer) {
    forEachModule(consumer);
  }

  @Override
  public void processModuleAndLibraries(@NotNull com.intellij.util.Consumer<? super JpsModule> moduleConsumer,
                                        @NotNull com.intellij.util.Consumer<? super JpsLibrary> libraryConsumer) {
    forEachModuleAndLibrary(moduleConsumer, libraryConsumer);
  }

  @Override
  public void processLibraries(final @NotNull com.intellij.util.Consumer<? super JpsLibrary> consumer) {
    forEachLibrary(consumer);
  }
}
