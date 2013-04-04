/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.change.*;
import com.intellij.openapi.externalSystem.model.project.change.user.*;
import com.intellij.openapi.externalSystem.model.project.id.*;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.IdeEntityVisitor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/20/13 3:14 PM
 */
public class EntityManageHelper {

  @NotNull private final ProjectStructureHelper     myProjectStructureHelper;
  @NotNull private final ExternalProjectManager     myProjectManager;
  @NotNull private final ExternalModuleManager      myModuleManager;
  @NotNull private final ExternalLibraryManager     myLibraryManager;
  @NotNull private final ExternalJarManager         myJarManager;
  @NotNull private final ExternalDependencyManager  myDependencyManager;
  @NotNull private final ExternalContentRootManager myContentRootManager;

  public EntityManageHelper(@NotNull ProjectStructureHelper helper,
                            @NotNull ExternalProjectManager projectManager,
                            @NotNull ExternalModuleManager moduleManager,
                            @NotNull ExternalLibraryManager libraryManager,
                            @NotNull ExternalJarManager jarManager,
                            @NotNull ExternalDependencyManager dependencyManager,
                            @NotNull ExternalContentRootManager contentRootManager)
  {
    myProjectStructureHelper = helper;
    myProjectManager = projectManager;
    myModuleManager = moduleManager;
    myLibraryManager = libraryManager;
    myJarManager = jarManager;
    myDependencyManager = dependencyManager;
    myContentRootManager = contentRootManager;
  }

  public void importEntities(@NotNull Project project, @NotNull Collection<ExternalEntity> entities, boolean synchronous) {
    final Set<ExternalModule> modules = ContainerUtilRt.newHashSet();
    final Map<ExternalModule, Collection<ExternalContentRoot>> contentRoots = ContainerUtilRt.newHashMap();
    final Set<ExternalLibrary> libraries = ContainerUtilRt.newHashSet();
    final Set<Jar> jars = ContainerUtilRt.newHashSet();
    final Map<ExternalModule, Collection<ExternalDependency>> dependencies = ContainerUtilRt.newHashMap();
    ExternalEntityVisitor visitor = new ExternalEntityVisitor() {
      @Override
      public void visit(@NotNull ExternalProject project) { }

      @Override
      public void visit(@NotNull ExternalModule module) { modules.add(module); }

      @Override
      public void visit(@NotNull ExternalLibrary library) { libraries.add(library); }

      @Override
      public void visit(@NotNull Jar jar) { jars.add(jar); }

      @Override
      public void visit(@NotNull ExternalModuleDependency dependency) { addDependency(dependency); }

      @Override
      public void visit(@NotNull ExternalLibraryDependency dependency) { addDependency(dependency); }

      @Override
      public void visit(@NotNull ExternalCompositeLibraryDependency dependency) { }

      @Override
      public void visit(@NotNull ExternalContentRoot contentRoot) {
        Collection<ExternalContentRoot> roots = contentRoots.get(contentRoot.getOwnerModule());
        if (roots == null) {
          contentRoots.put(contentRoot.getOwnerModule(), roots = ContainerUtilRt.<ExternalContentRoot>newHashSet());
        }
        roots.add(contentRoot);
      }

      private void addDependency(@NotNull ExternalDependency dependency) {
        Collection<ExternalDependency> d = dependencies.get(dependency.getOwnerModule());
        if (d == null) {
          dependencies.put(dependency.getOwnerModule(), d = ContainerUtilRt.<ExternalDependency>newHashSet());
        }
        d.add(dependency);
      }
    };
    
    // Sort entities.
    for (ExternalEntity entity : entities) {
      entity.invite(visitor);
    }
    myModuleManager.importModules(modules, project, false, synchronous);
    for (Map.Entry<ExternalModule, Collection<ExternalContentRoot>> entry : contentRoots.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey(), project);
      if (module != null) {
        myContentRootManager.importContentRoots(entry.getValue(), module, synchronous);
      }
    }
    myLibraryManager.importLibraries(libraries, project, synchronous);
    myJarManager.importJars(jars, project, synchronous);
    for (Map.Entry<ExternalModule, Collection<ExternalDependency>> entry : dependencies.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey(), project);
      if (module != null) {
        myDependencyManager.importDependencies(entry.getValue(), module, synchronous);
      }
    }
  }
  
  public void removeEntities(@NotNull Project project, @NotNull Collection<Object> entities, boolean synchronous) {
    final List<Module> modules = ContainerUtilRt.newArrayList();
    final List<ModuleAwareContentRoot> contentRoots = ContainerUtilRt.newArrayList();
    final List<ExportableOrderEntry> dependencies = ContainerUtilRt.newArrayList();
    final List<Jar> jars = ContainerUtilRt.newArrayList();
    IdeEntityVisitor ideVisitor = new IdeEntityVisitor() {
      @Override public void visit(@NotNull Project project) { }
      @Override public void visit(@NotNull Module module) { modules.add(module); }
      @Override public void visit(@NotNull ModuleAwareContentRoot contentRoot) { contentRoots.add(contentRoot); }
      @Override public void visit(@NotNull LibraryOrderEntry libraryDependency) { dependencies.add(libraryDependency); }
      @Override public void visit(@NotNull ModuleOrderEntry moduleDependency) { dependencies.add(moduleDependency); }
      @Override public void visit(@NotNull Library library) { }
    };
    ExternalEntityVisitor gradleVisitor = new ExternalEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull Jar jar) {
        jars.add(jar);
      }
    };
    for (Object entity : entities) {
      ExternalSystemUtil.dispatch(entity, gradleVisitor, ideVisitor);
    }

    myJarManager.removeJars(jars, project, synchronous);
    myContentRootManager.removeContentRoots(contentRoots, synchronous);
    myDependencyManager.removeDependencies(dependencies, synchronous);
    myModuleManager.removeModules(modules, synchronous);
  }

  /**
   * Tries to eliminate all target changes (namely, all given except those which correspond 'changes to preserve')
   *
   * @param changesToEliminate changes to eliminate
   * @param changesToPreserve  changes to preserve
   * @param synchronous        defines if the processing should be synchronous
   * @return non-processed changes
   */
  public Set<ExternalProjectStructureChange> eliminateChange(@NotNull Project project,
                                                             @NotNull Collection<ExternalProjectStructureChange> changesToEliminate,
                                                             @NotNull final Set<UserProjectChange<?>> changesToPreserve,
                                                             boolean synchronous)
  {
    
    EliminateChangesContext context = new EliminateChangesContext(
      project, myProjectStructureHelper, changesToPreserve, myProjectManager, myDependencyManager, synchronous
    );
    for (ExternalProjectStructureChange change : changesToEliminate) {
      change.invite(context.visitor);
    }

    removeEntities(project, context.entitiesToRemove, synchronous);
    importEntities(project, context.entitiesToImport, synchronous);
    return context.nonProcessedChanges;
  }

  private static void processProjectRenameChange(@NotNull GradleProjectRenameChange change, @NotNull EliminateChangesContext context) {
    context.projectManager.renameProject(change.getExternalValue(), context.project, context.synchronous);
  }

  // Don't auto-apply language level change because we can't correctly process language level change manually made
  // by a user - there is crazy processing related to project reloading after language level change and there is just
  // no normal way to inject there.
  
//  private static void processLanguageLevelChange(@NotNull GradleLanguageLevelChange change, @NotNull EliminateChangesContext context) {
//    context.projectManager.setLanguageLevel(change.getGradleValue(), context.projectStructureHelper.getProject(), context.synchronous);
//  }
  
  private static void processModulePresenceChange(@NotNull ModulePresenceChange change, @NotNull EliminateChangesContext context) {
    ModuleId id = change.getExternalEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      Module module = context.projectStructureHelper.findIdeModule(id.getModuleName(), context.project);
      if (module != null && !context.changesToPreserve.contains(new AddModuleUserChange(id.getModuleName()))) {
        context.entitiesToRemove.add(module);
        return;
      }
    }
    else {
      ExternalModule module = context.projectStructureHelper.findExternalModule(id.getModuleName(), id.getOwner(), context.project);
      if (module != null && !context.changesToPreserve.contains(new RemoveModuleUserChange(id.getModuleName()))) {
        context.entitiesToImport.add(module);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }

  private static void processContentRootPresenceChange(@NotNull ContentRootPresenceChange change,
                                                       @NotNull EliminateChangesContext context)
  {
    ContentRootId id = change.getExternalEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      ModuleAwareContentRoot root = context.projectStructureHelper.findIdeContentRoot(id, context.project);
      if (root != null) {
        context.entitiesToRemove.add(root);
        return;
      }
    }
    else {
      ExternalContentRoot root = context.projectStructureHelper.findExternalContentRoot(id, id.getOwner(), context.project);
      if (root != null) {
        context.entitiesToImport.add(root);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }
  
  private static void processLibraryDependencyPresenceChange(@NotNull LibraryDependencyPresenceChange change,
                                                             @NotNull EliminateChangesContext context)
  {
    LibraryDependencyId id = change.getExternalEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      LibraryOrderEntry dependency = context.projectStructureHelper.findIdeLibraryDependency(id, context.project);
      AddLibraryDependencyUserChange c = new AddLibraryDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToRemove.add(dependency);
        return;
      }
    }
    else {
      ProjectSystemId owner = id.getOwner();
      ExternalLibraryDependency dependency = context.projectStructureHelper.findExternalLibraryDependency(id, owner, context.project);
      RemoveLibraryDependencyUserChange c = new RemoveLibraryDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToImport.add(dependency);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }
  
  private static void processJarPresenceChange(@NotNull JarPresenceChange change, @NotNull EliminateChangesContext context) {
    JarId id = change.getExternalEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      Jar jar = context.projectStructureHelper.findIdeJar(id, context.project);
      if (jar != null) {
        context.entitiesToRemove.add(jar);
        return;
      }
    }
    else {
      ExternalLibrary library = context.projectStructureHelper.findExternalLibrary(id.getLibraryId(), id.getOwner(), context.project);
      if (library != null) {
        context.entitiesToImport.add(new Jar(id.getPath(), id.getLibraryPathType(), null, library, id.getOwner()));
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }

  private static void processModuleDependencyPresenceChange(@NotNull ModuleDependencyPresenceChange change,
                                                            @NotNull EliminateChangesContext context)
  {
    ModuleDependencyId id = change.getExternalEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      ModuleOrderEntry dependency = context.projectStructureHelper.findIdeModuleDependency(id, context.project);
      AddModuleDependencyUserChange c = new AddModuleDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToRemove.add(dependency);
        return;
      }
    }
    else {
      ExternalModuleDependency dependency = context.projectStructureHelper.findExternalModuleDependency(id, id.getOwner(), context.project);
      RemoveModuleDependencyUserChange c = new RemoveModuleDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToImport.add(dependency);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }
  
  private static void processDependencyScopeChange(@NotNull DependencyScopeChange change, @NotNull EliminateChangesContext context) {
    ExportableOrderEntry dependency = findDependency(change, context);
    if (dependency == null) {
      return;
    }
    AbstractExternalDependencyId id = change.getEntityId();
    UserProjectChange<?> userChange;
    if (dependency instanceof LibraryOrderEntry) {
      userChange = new LibraryDependencyScopeUserChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    else {
      userChange = new ModuleDependencyScopeUserChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    if (context.changesToPreserve.contains(userChange)) {
      context.nonProcessedChanges.add(change);
    }
    else {
      context.dependencyManager.setScope(change.getExternalValue(), dependency, context.synchronous);
    }
  }

  private static void processDependencyExportedStatusChange(@NotNull DependencyExportedChange change,
                                                            @NotNull EliminateChangesContext context)
  {
    ExportableOrderEntry dependency = findDependency(change, context);
    if (dependency == null) {
      return;
    }
    AbstractExternalDependencyId id = change.getEntityId();
    UserProjectChange<?> userChange;
    if (dependency instanceof LibraryOrderEntry) {
      userChange = new LibraryDependencyExportedChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    else {
      userChange = new ModuleDependencyExportedChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    if (context.changesToPreserve.contains(userChange)) {
      context.nonProcessedChanges.add(change);
    }
    else {
      context.dependencyManager.setExported(change.getExternalValue(), dependency, context.synchronous);
    }
  }

  @Nullable
  private static ExportableOrderEntry findDependency(@NotNull AbstractConflictingPropertyChange<?> change,
                                                     @NotNull EliminateChangesContext context)
  {
    ProjectEntityId id = change.getEntityId();
    ExportableOrderEntry dependency = null;
    if (id instanceof LibraryDependencyId) {
      dependency = context.projectStructureHelper.findIdeLibraryDependency((LibraryDependencyId)id, context.project);
    }
    else if (id instanceof ModuleDependencyId) {
      dependency = context.projectStructureHelper.findIdeModuleDependency((ModuleDependencyId)id, context.project);
    }
    else {
      context.nonProcessedChanges.add(change);
    }
    return dependency;
  }
  
  private static class EliminateChangesContext {
    @NotNull final Set<Object>                         entitiesToRemove    = ContainerUtilRt.newHashSet();
    @NotNull final Set<ExternalEntity>                 entitiesToImport    = ContainerUtilRt.newHashSet();
    @NotNull final Set<ExternalProjectStructureChange> nonProcessedChanges = ContainerUtilRt.newHashSet();
    @NotNull final Set<UserProjectChange<?>>           changesToPreserve   = ContainerUtilRt.newHashSet();

    @NotNull final Project                   project;
    @NotNull final ProjectStructureHelper    projectStructureHelper;
    @NotNull final ExternalProjectManager    projectManager;
    @NotNull final ExternalDependencyManager dependencyManager;
    final          boolean                   synchronous;

    @NotNull ExternalProjectStructureChangeVisitor visitor = new ExternalProjectStructureChangeVisitor() {
      @Override
      public void visit(@NotNull GradleProjectRenameChange change) {
        processProjectRenameChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull LanguageLevelChange change) {
//        processLanguageLevelChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull ModulePresenceChange change) {
        processModulePresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull ContentRootPresenceChange change) {
        processContentRootPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull LibraryDependencyPresenceChange change) {
        processLibraryDependencyPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull JarPresenceChange change) {
        processJarPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull OutdatedLibraryVersionChange change) {
      }

      @Override
      public void visit(@NotNull ModuleDependencyPresenceChange change) {
        processModuleDependencyPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull DependencyScopeChange change) {
        processDependencyScopeChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull DependencyExportedChange change) {
        processDependencyExportedStatusChange(change, EliminateChangesContext.this);
      }
    };

    EliminateChangesContext(@NotNull Project project,
                            @NotNull ProjectStructureHelper projectStructureHelper,
                            @NotNull Set<UserProjectChange<?>> changesToPreserve,
                            @NotNull ExternalProjectManager projectManager,
                            @NotNull ExternalDependencyManager dependencyManager,
                            boolean synchronous)
    {
      this.project = project;
      this.projectStructureHelper = projectStructureHelper;
      this.changesToPreserve.addAll(changesToPreserve);
      this.projectManager = projectManager;
      this.dependencyManager = dependencyManager;
      this.synchronous = synchronous;
    }
  }
}
