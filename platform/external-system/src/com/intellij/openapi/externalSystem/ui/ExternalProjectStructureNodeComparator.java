package com.intellij.openapi.externalSystem.ui;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.IdeEntityVisitor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Encapsulates logic of comparing 'sync project structures' tree nodes.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/21/12 2:44 PM
 */
public class ExternalProjectStructureNodeComparator implements Comparator<ProjectStructureNode<?>> {

  private static final int PROJECT_WEIGHT            = 0;
  private static final int MODULE_WEIGHT             = 1;
  private static final int CONTENT_ROOT_WEIGHT       = 2;
  private static final int SYNTHETIC_WEIGHT          = 3;
  private static final int MODULE_DEPENDENCY_WEIGHT  = 4;
  private static final int LIBRARY_DEPENDENCY_WEIGHT = 5;
  private static final int LIBRARY_WEIGHT            = 6;
  private static final int JAR_WEIGHT                = 7;
  private static final int UNKNOWN_WEIGHT            = 20;

  @NotNull private final ProjectStructureServices myContext;
  @NotNull private final Project myProject;

  public ExternalProjectStructureNodeComparator(@NotNull ProjectStructureServices context, @NotNull Project project) {
    myContext = context;
    myProject = project;
  }

  @Override
  public int compare(ProjectStructureNode<?> n1, ProjectStructureNode<?> n2) {
    final ProjectStructureNodeDescriptor<? extends ProjectEntityId> d1 = n1.getDescriptor();
    final ProjectEntityId id1 = d1.getElement();

    final ProjectStructureNodeDescriptor<? extends ProjectEntityId> d2 = n2.getDescriptor();
    final ProjectEntityId id2 = d2.getElement();

    // Put 'gradle-local' nodes at the top.
    if (!ProjectSystemId.IDE.equals(id1.getOwner())&& ProjectSystemId.IDE.equals(id2.getOwner())) {
      return -1;
    }
    else if (ProjectSystemId.IDE.equals(id1.getOwner()) && !ProjectSystemId.IDE.equals(id2.getOwner())) {
      return 1;
    }

    // Compare by weight.
    int weight1 = getWeight(id1);
    int weight2 = getWeight(id2);
    if (weight1 != weight2) {
      return weight1 - weight2;
    }

    // Compare by name.
    return d1.getName().compareTo(d2.getName());
  }

  private int getWeight(@NotNull ProjectEntityId id) {
    if (id.getType() == ProjectEntityType.SYNTHETIC) {
      return SYNTHETIC_WEIGHT;
    }
    else if (id.getType() == ProjectEntityType.JAR) {
      return JAR_WEIGHT;
    }
    Object entity = id.mapToEntity(myContext, myProject);
    if (entity instanceof AbstractCompositeData) {
      entity = ((AbstractCompositeData)entity).getIdeEntity();
    }
    final Ref<Integer> result = new Ref<Integer>();
    if (entity instanceof ProjectEntityData) {
      // TODO den implement
//      ((ProjectEntityData)entity).invite(new ExternalEntityVisitor() {
//        @Override
//        public void visit(@NotNull ProjectData project) {
//          result.set(PROJECT_WEIGHT);
//        }
//
//        @Override
//        public void visit(@NotNull ModuleData module) {
//          result.set(MODULE_WEIGHT);
//        }
//
//        @Override
//        public void visit(@NotNull ContentRootData contentRoot) {
//          result.set(CONTENT_ROOT_WEIGHT);
//        }
//
//        @Override
//        public void visit(@NotNull LibraryData library) {
//          result.set(LIBRARY_WEIGHT);
//        }
//
//        @Override
//        public void visit(@NotNull JarData jar) {
//          result.set(JAR_WEIGHT);
//        }
//
//        @Override
//        public void visit(@NotNull ModuleDependencyData dependency) {
//          result.set(MODULE_DEPENDENCY_WEIGHT);
//        }
//
//        @Override
//        public void visit(@NotNull LibraryDependencyData dependency) {
//          result.set(LIBRARY_DEPENDENCY_WEIGHT);
//        }
//
//        @Override
//        public void visit(@NotNull CompositeLibraryDependencyData dependency) {
//          result.set(LIBRARY_DEPENDENCY_WEIGHT);
//        }
//      });
    }
    else {
      ExternalSystemUtil.dispatch(entity, new IdeEntityVisitor() {
        @Override
        public void visit(@NotNull Project project) {
          result.set(PROJECT_WEIGHT);
        }

        @Override
        public void visit(@NotNull Module module) {
          result.set(MODULE_WEIGHT);
        }

        @Override
        public void visit(@NotNull ModuleAwareContentRoot contentRoot) {
          int i = 0;
          for (OrderEntry entry : myContext.getPlatformFacade().getOrderEntries(contentRoot.getModule())) {
            if (entry instanceof ModuleSourceOrderEntry) {
              result.set(i);
              return;
            }
            i++;
          }
          result.set(CONTENT_ROOT_WEIGHT);
        }

        @Override
        public void visit(@NotNull LibraryOrderEntry libraryDependency) {
          result.set(getWeight(libraryDependency.getOwnerModule(), libraryDependency));
        }

        @Override
        public void visit(@NotNull ModuleOrderEntry moduleDependency) {
          result.set(getWeight(moduleDependency.getOwnerModule(), moduleDependency));
        }

        @Override
        public void visit(@NotNull Library library) {
          result.set(LIBRARY_WEIGHT);
        }
      });
    }
    final Integer i = result.get();
    return i == null ? UNKNOWN_WEIGHT : i;
  }

  private int getWeight(@NotNull Module module, @NotNull Object entry) {
    int i = 0;
    for (OrderEntry orderEntry : myContext.getPlatformFacade().getOrderEntries(module)) {
      if (orderEntry.equals(entry)) {
        return i;
      }
      i++;
    }
    return UNKNOWN_WEIGHT;
  }
}
