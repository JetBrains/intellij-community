package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.id.ContentRootId;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNodeDescriptor;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;

import java.util.Collection;
import java.util.Set;

/**
 * Gradle integration shows a project structure tree which contain nodes for gradle-local entities (modules, libraries etc).
 * End-user can select interested nodes and import them into the current intellij project.
 * <p/>
 * This class helps during that at the following ways:
 * <pre>
 * <ul>
 *   <li>filters out non gradle-local nodes;</li>
 *   <li>
 *     collects all nodes that should be imported. For example, an user can mark 'module' node to import. We need to import not
 *     only that module but its (transitive) dependencies as well. I.e. basically the algorithm looks like 'import all entities up
 *     to the path to root and all sub-entities';
 *   </li>
 *   <li>
 *     sorts entities to import in topological order. Example: let module<sub>1</sub> depend on module<sub>2</sub>. We need to
 *     import module<sub>2</sub> before module<sub>1</sub> then;
 *   </li>
 * </ul>
 * </pre>
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/10/12 11:51 AM
 */
public class GradleLocalNodeManageHelper {

  @NotNull private final ProjectStructureHelper myProjectStructureHelper;
  @NotNull private final EntityIdMapper         myIdMapper;
  @NotNull private final EntityManageHelper     myEntityManageHelper;

  public GradleLocalNodeManageHelper(@NotNull ProjectStructureHelper projectStructureHelper,
                                     @NotNull EntityIdMapper idMapper,
                                     @NotNull EntityManageHelper entityManageHelper)
  {
    myProjectStructureHelper = projectStructureHelper;
    myIdMapper = idMapper;
    myEntityManageHelper = entityManageHelper;
  }

  /**
   * {@link #deriveEntitiesToImport(Project, Iterable)} Derives} target entities from the given nodes
   * and {@link EntityManageHelper#importEntities(Project, Collection, boolean) imports them}.
   *
   * @param project  target ide project
   * @param nodes  'anchor nodes' to import
   */
  public void importNodes(@NotNull Project project, @NotNull Iterable<ProjectStructureNode<?>> nodes) {
    final Collection<ProjectEntityData> entities = deriveEntitiesToImport(project, nodes);
    myEntityManageHelper.importEntities(project, entities, true);
  }

  /**
   * Collects all nodes that should be imported based on the given nodes and returns corresponding gradle entities
   * sorted in topological order.
   *
   * @param nodes  'anchor nodes' to import
   * @return collection of gradle entities that should be imported based on the given nodes
   */
  @NotNull
  public Collection<ProjectEntityData> deriveEntitiesToImport(@NotNull Project project, @NotNull Iterable<ProjectStructureNode<?>> nodes) {
    Context context = new Context(project);
    for (ProjectStructureNode<?> node : nodes) {
      collectEntitiesToImport(node, context);
    }
    return context.entities;
  }

  private void collectEntitiesToImport(@NotNull ProjectStructureNode<?> node, @NotNull Context context) {
    // Collect up.
    for (ProjectStructureNode<?> n = node.getParent(); n != null; n = n.getParent()) {
      final ProjectStructureNodeDescriptor<?> descriptor = n.getDescriptor();
      if (n.getDescriptor().getElement().getType() == ProjectEntityType.SYNTHETIC) {
        continue;
      }
      if (descriptor.getAttributes() != ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE) {
        break;
      }
      Object id = descriptor.getElement();
      final Object entity = myIdMapper.mapIdToEntity((ProjectEntityId)id, context.project);
      if (entity instanceof ProjectEntityData) {
        // TODO den implement
//        ((ProjectEntityData)entity).invite(context.visitor);
      }
    }

    // Collect down.
    final Stack<ProjectEntityData> toProcess = new Stack<ProjectEntityData>();
    final Object id = node.getDescriptor().getElement();
    final Object entity = myIdMapper.mapIdToEntity((ProjectEntityId)id, context.project);
    if (entity instanceof ProjectEntityData) {
      toProcess.push((ProjectEntityData)entity);
    }

    context.recursive = true;
    while (!toProcess.isEmpty()) {
      final ProjectEntityData e = toProcess.pop();
      // TODO den implement
//      e.invite(context.visitor);
    }
  }

  private void collectModuleEntities(@NotNull ModuleData module, @NotNull Context context) {
    final Module intellijModule = myProjectStructureHelper.findIdeModule(module.getName(), context.project);
    if (intellijModule != null) {
      // Already imported
      return;
    }
    context.entities.add(module);
    if (!context.recursive) {
      return;
    }
    // TODO den implement
//    for (ContentRootData contentRoot : module.getContentRoots()) {
//      contentRoot.invite(context.visitor);
//    }
//    for (DependencyData dependency : module.getDependencies()) {
//      dependency.invite(context.visitor);
//    }
  }

  private void collectContentRoots(@NotNull ContentRootData contentRoot, @NotNull Context context) {
    final ContentRootId id = EntityIdMapper.mapEntityToId(contentRoot);
    final ModuleAwareContentRoot intellijContentRoot = myProjectStructureHelper.findIdeContentRoot(id, context.project);
    if (intellijContentRoot != null) {
      // Already imported.
      return;
    }
    context.entities.add(contentRoot);
  }
  
  private void collectModuleDependencyEntities(@NotNull ModuleDependencyData dependency, @NotNull Context context) {
    final ModuleOrderEntry intellijModuleDependency = myProjectStructureHelper.findIdeModuleDependency(dependency, context.project);
    if (intellijModuleDependency != null) {
      // Already imported.
      return;
    }
    context.entities.add(dependency);
    final ModuleData gradleModule = dependency.getTarget();
    final Module intellijModule = myProjectStructureHelper.findIdeModule(gradleModule, context.project);
    if (intellijModule != null) {
      return;
    }
    boolean r = context.recursive;
    context.recursive = true;
    try {
      // TODO den implement
//      gradleModule.invite(context.visitor);
    }
    finally {
      context.recursive = r;
    }
  }

  private void collectLibraryDependencyEntities(@NotNull LibraryDependencyData dependency, @NotNull Context context) {
    final LibraryOrderEntry intellijDependency
      = myProjectStructureHelper.findIdeLibraryDependency(dependency.getOwnerModule().getName(), dependency.getName(), context.project);
    Set<String> intellijPaths = ContainerUtilRt.newHashSet();
    LibraryData gradleLibrary = dependency.getTarget();
    Library intellijLibrary = null;
    if (intellijDependency == null) {
      context.entities.add(dependency);
    }
    else {
      intellijLibrary = intellijDependency.getLibrary();
    }

    if (intellijLibrary == null) {
      intellijLibrary = myProjectStructureHelper.findIdeLibrary(gradleLibrary, context.project);
    }

    if (intellijLibrary == null) {
      context.entities.add(gradleLibrary);
    }
    else {
      for (VirtualFile jarFile : intellijLibrary.getFiles(OrderRootType.CLASSES)) {
        intellijPaths.add(ExternalSystemUtil.getLocalFileSystemPath(jarFile));
      }
    }
    
    for (String gradleJarPath : gradleLibrary.getPaths(LibraryPathType.BINARY)) {
      if (!intellijPaths.contains(gradleJarPath)) {
        context.entities.add(new JarData(gradleJarPath, LibraryPathType.BINARY, null, gradleLibrary, dependency.getOwner()));
      }
    }
  }

  public void removeNodes(@NotNull Project project, @NotNull Collection<ProjectStructureNode<?>> nodes) {
    Collection<Object> entities = ContainerUtilRt.newArrayList();
    for (ProjectStructureNode<?> node : nodes) {
      ProjectStructureNodeDescriptor<? extends ProjectEntityId> descriptor = node.getDescriptor();
      if (descriptor.getAttributes() != ExternalSystemTextAttributes.IDE_LOCAL_CHANGE) {
        continue;
      }
      Object entity = myIdMapper.mapIdToEntity(descriptor.getElement(), project);
      if (entity != null) {
        entities.add(entity);
      }
    }
    myEntityManageHelper.removeEntities(project, entities, true);
  }
  
  private class Context {

    @NotNull public final Set<ProjectEntityData> entities = ContainerUtilRt.newHashSet();

    @NotNull public final Project project;

    public boolean recursive;

    Context(@NotNull Project project) {
      this.project = project;
    }
  }
}
