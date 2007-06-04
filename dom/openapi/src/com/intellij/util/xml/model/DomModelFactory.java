/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.util.xml.model;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ModelMerger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class DomModelFactory<T extends DomElement, M extends DomModel<T>, C extends PsiElement> extends SimpleDomModelFactory<T> {

  private final DomModelCache<M, XmlFile> myModelCache;
  private final DomModelCache<M, Module> myCombinedModelCache;
  private final DomModelCache<List<M>, Module> myAllModelsCache;


  protected DomModelFactory(@NotNull Class<T> aClass,
                          @NotNull ModelMerger modelMerger,
                          final Project project,
                          @NonNls String name) {
    super(aClass, modelMerger);

    myModelCache = new DomModelCache<M, XmlFile>(project, name + " model") {
       @NotNull
       protected CachedValueProvider.Result<M> computeValue(@NotNull XmlFile file) {
         final PsiFile originalFile = file.getOriginalFile();
         if (originalFile != null) {
           file = (XmlFile)originalFile;
         }

         final Module module = ModuleUtil.findModuleForPsiElement(file);
         final M model = computeModel(file, module);
         return new CachedValueProvider.Result<M>(model, computeDependencies(model, module));
      }
    };

    myCombinedModelCache = new DomModelCache<M, Module>(project, name + " combined model") {
      @NotNull
      protected CachedValueProvider.Result<M> computeValue(@NotNull final Module module) {
        final M combinedModel = computeCombinedModel(module);
        return new CachedValueProvider.Result<M>(combinedModel, computeDependencies(combinedModel, module));
      }
    };

    myAllModelsCache = new DomModelCache<List<M>, Module>(project, name + " models list") {
      @NotNull
      protected CachedValueProvider.Result<List<M>> computeValue(@NotNull final Module module) {
        final List<M> models = computeAllModels(module);
        return new CachedValueProvider.Result<List<M>>(models, computeDependencies(null, module));
      }
    };
  }

  @NotNull
  public Object[] computeDependencies(@Nullable M model, @Nullable Module module) {

    final ArrayList<Object> dependencies = new ArrayList<Object>();
    final Set<XmlFile> files;
    if (model != null) {
      files = model.getConfigFiles();
      dependencies.addAll(files);
    } else {
      dependencies.add(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }
    if (module != null) {
      dependencies.add(ProjectRootManager.getInstance(module.getProject()));
    }
    return dependencies.toArray(new Object[dependencies.size()]);
  }

  @Nullable
  public abstract M getModel(@NotNull C context);

  @NotNull
  public List<M> getAllModels(@NotNull Module module) {

    final List<M> models = myAllModelsCache.getCachedValue(module);
    if (models == null) {
      return Collections.emptyList();
    }
    else {
      return models;
    }
  }

  @Nullable
  protected abstract List<M> computeAllModels(@NotNull Module module);

  @Nullable
  public M getModelByConfigFile(@Nullable XmlFile psiFile) {
    if (psiFile == null) {
      return null;
    }
    return myModelCache.getCachedValue(psiFile);
  }

  @Nullable
  protected M computeModel(@NotNull XmlFile psiFile, @Nullable Module module) {
    if (module == null) {
      return null;
    }
    final List<M> models = getAllModels(module);
    for (M model: models) {
      final Set<XmlFile> configFiles = model.getConfigFiles();
      if (configFiles.contains(psiFile)) {
        return model;
      }
    }
    return null;
  }

  @Nullable
  public M getCombinedModel(@Nullable Module module) {
    if (module == null) {
      return null;
    }
    return myCombinedModelCache.getCachedValue(module);
  }

  @Nullable
  protected M computeCombinedModel(@NotNull Module module) {
    final List<M> models = getAllModels(module);
    switch (models.size()) {
      case 0:
        return null;
      case 1:
        return models.get(0);
    }
    final Set<XmlFile> configFiles = new LinkedHashSet<XmlFile>();
    final LinkedHashSet<T> list = new LinkedHashSet<T>(models.size());
    for (M model: models) {
      final Set<XmlFile> files = model.getConfigFiles();
      for (XmlFile file: files) {
        ContainerUtil.addIfNotNull(getDom(file), list);
      }
      configFiles.addAll(files);
    }
    final T mergedModel = getModelMerger().mergeModels(getClazz(), list);
    final M firstModel = models.get(0);
    return createCombinedModel(configFiles, mergedModel, firstModel, module);
  }

  /**
   * Factory method to create combined model for given module.
   * Used by {@link #computeCombinedModel(com.intellij.openapi.module.Module)}.
   *
   * @param configFiles file set including all files for all models returned by {@link #getAllModels(com.intellij.openapi.module.Module)}.
   * @param mergedModel merged model for all models returned by {@link #getAllModels(com.intellij.openapi.module.Module)}.
   * @param firstModel the first model returned by {@link #getAllModels(com.intellij.openapi.module.Module)}.
   * @param module
   * @return combined model.
   */
  protected abstract M createCombinedModel(Set<XmlFile> configFiles, T mergedModel, M firstModel, final Module module);

  @NotNull
  public Set<XmlFile> getConfigFiles(@Nullable C context) {
    if (context == null) {
      return Collections.emptySet();
    }
    final M model = getModel(context);
    if (model == null) {
      return Collections.emptySet();
    }
    else {
      return model.getConfigFiles();
    }
  }

  @NotNull
  public Set<XmlFile> getAllConfigFiles(@NotNull Module module) {
    final HashSet<XmlFile> xmlFiles = new HashSet<XmlFile>();
    for (M model: getAllModels(module)) {
      xmlFiles.addAll(model.getConfigFiles());
    }
    return xmlFiles;
  }

  public List<DomFileElement<T>> getFileElements(M model) {
    final ArrayList<DomFileElement<T>> list = new ArrayList<DomFileElement<T>>(model.getConfigFiles().size());
    for (XmlFile configFile: model.getConfigFiles()) {
      final DomFileElement<T> element = DomManager.getDomManager(configFile.getProject()).getFileElement(configFile, myClass);
      if (element != null) {
        list.add(element);
      }
    }
    return list;
  }

  public ModelMerger getModelMerger() {
    return myModelMerger;
  }

  public Class<T> getClazz() {
    return myClass;
  }
}
