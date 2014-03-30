/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.module.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.*;

import java.util.Collection;
import java.util.LinkedHashSet;
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
  private Condition<JpsDependencyElement> myCondition;

  protected JpsDependenciesEnumeratorBase(Collection<JpsModule> rootModules) {
    myRootModules = rootModules;
  }

  @NotNull
  @Override
  public Self withoutLibraries() {
    myWithoutLibraries = true;
    return self();
  }

  @NotNull
  @Override
  public Self withoutDepModules() {
    myWithoutDepModules = true;
    return self();
  }

  @NotNull
  @Override
  public Self withoutSdk() {
    myWithoutSdk = true;
    return self();
  }

  @NotNull
  @Override
  public Self withoutModuleSourceEntries() {
    myWithoutModuleSourceEntries = true;
    return self();
  }

  @NotNull
  @Override
  public Self satisfying(@NotNull Condition<JpsDependencyElement> condition) {
    myCondition = condition;
    return self();
  }

  @NotNull
  @Override
  public Self recursively() {
    myRecursively = true;
    return self();
  }

  protected abstract Self self();

  @NotNull
  @Override
  public Set<JpsModule> getModules() {
    Set<JpsModule> result = new LinkedHashSet<JpsModule>();
    processModules(new CollectConsumer<JpsModule>(result));
    return result;
  }

  @Override
  public void processModules(@NotNull final Consumer<JpsModule> consumer) {
    processDependencies(new Processor<JpsDependencyElement>() {
      @Override
      public boolean process(JpsDependencyElement dependencyElement) {
        if (myRecursively && dependencyElement instanceof JpsModuleSourceDependency) {
          consumer.consume(dependencyElement.getContainingModule());
        }
        else if ((!myRecursively || !shouldProcessDependenciesRecursively())&& dependencyElement instanceof JpsModuleDependency) {
          JpsModule module = ((JpsModuleDependency)dependencyElement).getModule();
          if (module != null) {
            consumer.consume(module);
          }
        }
        return true;
      }
    });
  }

  protected boolean shouldProcessDependenciesRecursively() {
    return true;
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

  @NotNull
  @Override
  public Set<JpsLibrary> getLibraries() {
    Set<JpsLibrary> libraries = new LinkedHashSet<JpsLibrary>();
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
