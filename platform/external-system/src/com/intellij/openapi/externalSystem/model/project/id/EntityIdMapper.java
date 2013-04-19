package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.IdeEntityVisitor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates functionality of mapping project structure entities (mutable object) to id object (immutable) and vice versa.
 * That's why we provide {@code 'entity <--> id'} mapping and make it possible to store the unique 'id' element within the node.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2/14/12 12:20 PM
 * @see ProjectEntityId
 */
public class EntityIdMapper {

  @NotNull private final ProjectStructureServices myServices;

  public EntityIdMapper(@NotNull ProjectStructureServices services) {
    myServices = services;
  }

  /**
   * Performs {@code 'entity -> id'} mapping. Check class-level javadoc for more details.
   *
   * @param entity  target entity to map
   * @return        'id object' mapped to the given entity in case of successful match; <code>null</code> otherwise
   * @throws IllegalArgumentException   if it's not possible to map given entity to id
   */
  @SuppressWarnings({"MethodMayBeStatic", "unchecked"})
  @NotNull
  public static <T extends ProjectEntityId> T mapEntityToId(@NotNull Object entity) throws IllegalArgumentException {
    final Ref<ProjectEntityId> result = new Ref<ProjectEntityId>();
    if (entity instanceof DataNode) {
      Object data = ((DataNode)entity).getData();
      if (data instanceof ProjectEntityData) {
        return (T)((ProjectEntityData)data).getId((DataNode<T>)entity);
      }
    }

    if (result.get() == null) {
      ExternalSystemUtil.dispatch(entity, new IdeEntityVisitor() {
        @Override
        public void visit(@NotNull Project project) {
          result.set(new ProjectId(ProjectSystemId.IDE));
        }

        @Override
        public void visit(@NotNull Module module) {
          result.set(new ModuleId(ProjectSystemId.IDE, module.getName()));
        }

        @Override
        public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
          final String path = contentRoot.getFile().getPath();
          result.set(new ContentRootId(ProjectSystemId.IDE, contentRoot.getModule().getName(), path));
        }

        @Override
        public void visit(@NotNull LibraryOrderEntry libraryDependency) {
          String libraryName = libraryDependency.getLibraryName();
          if (libraryName == null) {
            final Library library = libraryDependency.getLibrary();
            if (library != null) {
              libraryName = ExternalSystemUtil.getLibraryName(library);
            }
          }
          if (libraryName == null) {
            return;
          }
          result.set(new LibraryDependencyId(ProjectSystemId.IDE, libraryDependency.getOwnerModule().getName(), libraryName));
        }

        @Override
        public void visit(@NotNull ModuleOrderEntry moduleDependency) {
          result.set(new ModuleDependencyId(
            ProjectSystemId.IDE, moduleDependency.getOwnerModule().getName(), moduleDependency.getModuleName()
          ));
        }

        @Override
        public void visit(@NotNull Library library) {
          result.set(new LibraryId(ProjectSystemId.IDE, ExternalSystemUtil.getLibraryName(library)));
        }
      });
    }
    final Object r = result.get();
    if (r == null) {
      throw new IllegalArgumentException(String.format("Can't map entity '%s' to id element", entity));
    }
    return (T)r;
  }

  /**
   * Performs {@code 'id -> entity'} mapping. Check class-level javadoc for more details.
   *
   * @param id          target entity id
   * @param ideProject  target ide project
   * @param <T>         target entity type
   * @return            entity mapped to the given id if any; <code>null</code> otherwise
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T mapIdToEntity(@NotNull ProjectEntityId id, @NotNull Project ideProject) {
    return (T)id.mapToEntity(myServices, ideProject);
  }
}
