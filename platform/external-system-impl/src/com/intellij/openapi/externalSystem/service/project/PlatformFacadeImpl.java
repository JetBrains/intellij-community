package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 1/26/12 11:54 AM
 */
public class PlatformFacadeImpl implements PlatformFacade {

  @NotNull
  @Override
  public LibraryTable getProjectLibraryTable(@NotNull Project project) {
    return ProjectLibraryTable.getInstance(project);
  }

  @NotNull
  @Override
  public Collection<Module> getModules(@NotNull Project project) {
    return Arrays.asList(ModuleManager.getInstance(project).getModules());
  }

  @NotNull
  @Override
  public Collection<OrderEntry> getOrderEntries(@NotNull Module module) {
    return Arrays.asList(ModuleRootManager.getInstance(module).getOrderEntries());
  }

  @NotNull
  @Override
  public String getLocalFileSystemPath(@NotNull VirtualFile file) {
    return ExternalSystemApiUtil.getLocalFileSystemPath(file);
  }
}
