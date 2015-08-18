package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * IntelliJ Platform code provides a lot of statical bindings to the interested pieces of data. For example we need to execute code
 * like below to get list of modules for the target project:
 * <pre>
 *   ModuleManager.getInstance(project).getModules()
 * </pre>
 * That means that it's not possible to test target classes in isolation if corresponding infrastructure is not set up.
 * However, we don't want to set it up if we execute a simple standalone test.
 * <p/>
 * This interface is intended to encapsulate access to the underlying ide platform functionality.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/26/12 11:32 AM
 */
public interface PlatformFacade {

  @NotNull
  LibraryTable getProjectLibraryTable(@NotNull Project project);
  
  @NotNull
  Collection<Module> getModules(@NotNull Project project);

  @NotNull
  Collection<Module> getModules(@NotNull Project project, @NotNull ProjectData projectData);

  @NotNull
  Collection<OrderEntry> getOrderEntries(@NotNull Module module);

  /**
   * Allows to derive from the given VFS file path that may be compared to the path used by the gradle api.
   * <p/>
   * Generally, this method is necessary for processing binary library paths - they point to jar files and VFS uses
   * <code>'!'</code> marks in their paths internally.
   * 
   * @param file  target file
   * @return      given file's path that may be compared to the one used by the gradle api
   */
  @NotNull
  String getLocalFileSystemPath(@NotNull VirtualFile file);

  /**
   * Creates a module of the specified type at the specified path and adds it to the project
   * to which the module manager is related. {@link #commit()} must be called to
   * bring the changes in effect.
   *
   *
   * @param project
   * @param filePath the path at which the module is created.
   * @param moduleTypeId the ID of the module type to create.
   * @return the module instance.
   */
  Module newModule(Project project, @NotNull @NonNls String filePath, final String moduleTypeId);

  ModifiableRootModel getModuleModifiableModel(Module module);

  @Nullable
  Module findIdeModule(@NotNull ModuleData module, @NotNull Project ideProject);

  @Nullable
  Module findIdeModule(@NotNull String ideModuleName, @NotNull Project ideProject);

  @Nullable
  Library findIdeLibrary(@NotNull LibraryData libraryData, @NotNull Project ideProject);

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  ModuleOrderEntry findIdeModuleDependency(@NotNull ModuleDependencyData dependency, @NotNull ModifiableRootModel model);

  @Nullable
  OrderEntry findIdeModuleOrderEntry(LibraryDependencyData data, Project project);
}
