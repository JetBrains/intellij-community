package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.project.ExternalContentRoot;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.model.project.SourceType;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;

import java.util.Collection;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 3:20 PM
 */
public class ExternalContentRootManager {

  @SuppressWarnings("MethodMayBeStatic")
  public void importContentRoots(@NotNull final Collection<ExternalContentRoot> contentRoots,
                                 @NotNull final Module module,
                                 boolean synchronous)
  {
    if (contentRoots.isEmpty()) {
      return;
    }
    ExternalSystemUtil.executeProjectChangeAction(module.getProject(), contentRoots, synchronous, new Runnable() {
      @Override
      public void run() {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel model = moduleRootManager.getModifiableModel();
        try {
          for (ExternalContentRoot contentRoot : contentRoots) {
            ContentEntry contentEntry = model.addContentEntry(toVfsUrl(contentRoot.getRootPath()));
            for (String path : contentRoot.getPaths(SourceType.SOURCE)) {
              contentEntry.addSourceFolder(toVfsUrl(path), false);
            }
            for (String path : contentRoot.getPaths(SourceType.TEST)) {
              contentEntry.addSourceFolder(toVfsUrl(path), true);
            }
            for (String path : contentRoot.getPaths(SourceType.EXCLUDED)) {
              contentEntry.addExcludeFolder(toVfsUrl(path));
            }
          }
        }
        finally {
          model.commit();
        }
      }
    });
  }

  private static String toVfsUrl(@NotNull String path) {
    return LocalFileSystem.PROTOCOL_PREFIX + path;
  }
  
  @SuppressWarnings("MethodMayBeStatic")
  public void removeContentRoots(@NotNull final Collection<ModuleAwareContentRoot> contentRoots, boolean synchronous) {
    if (contentRoots.isEmpty()) {
      return;
    }
    Project project = contentRoots.iterator().next().getModule().getProject();
    ExternalSystemUtil.executeProjectChangeAction(project, contentRoots, synchronous, new Runnable() {
      @Override
      public void run() {
        for (ModuleAwareContentRoot contentRoot : contentRoots) {
          final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(contentRoot.getModule());
          ModifiableRootModel model = moduleRootManager.getModifiableModel();
          try {
            model.removeContentEntry(contentRoot);
          }
          finally {
            model.commit();
          }
        } 
      }
    });
  }
}