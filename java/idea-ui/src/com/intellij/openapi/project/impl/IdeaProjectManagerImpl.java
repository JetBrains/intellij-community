package com.intellij.openapi.project.impl;

import com.intellij.conversion.ConversionService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author mike
 */
public class IdeaProjectManagerImpl extends ProjectManagerImpl {
  public IdeaProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx) {
    super(virtualFileManagerEx);
  }

  @Nullable
  protected Pair<Class, Object> convertProject(final String filePath) throws ProcessCanceledException {
    final String fp = canonicalize(filePath);

    final File f = new File(fp);
    if (fp != null && f.exists() && f.isFile() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final boolean converted = ConversionService.getInstance().convert(fp);
      if (!converted) {
        throw new ProcessCanceledException();
      }
    }
    return null;
  }
}
