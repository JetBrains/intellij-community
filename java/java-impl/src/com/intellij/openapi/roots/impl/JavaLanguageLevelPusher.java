package com.intellij.openapi.roots.impl;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public class JavaLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {

  public static void pushLanguageLevel(final Project project) {
    PushedFilePropertiesUpdater.getInstance(project).pushAll(new JavaLanguageLevelPusher());
  }

  public void initExtra(Project project, MessageBus bus, Engine languageLevelUpdater) {
    // nothing
  }

  @NotNull
  public Key<LanguageLevel> getFileDataKey() {
    return LanguageLevel.KEY;
  }

  public boolean pushDirectoriesOnly() {
    return true;
  }

  public LanguageLevel getDefaultValue() {
    return LanguageLevel.HIGHEST;
  }

  public LanguageLevel getImmediateValue(Project project, VirtualFile file) {
    return null;
  }

  public LanguageLevel getImmediateValue(Module module) {
    return LanguageLevelUtil.getEffectiveLanguageLevel(module);
  }

  public boolean acceptsFile(VirtualFile file) {
    return false;
  }

  private static final FileAttribute PERSISTENCE = new FileAttribute("language_level_persistence", 1);

  public void persistAttribute(VirtualFile fileOrDir, LanguageLevel level) throws IOException {
    final DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir);
    if (iStream != null) {
      try {
        final int oldLevelOrdinal = iStream.readInt();
        if (oldLevelOrdinal == level.ordinal()) return;
      }
      finally {
        iStream.close();
      }
    }

    final DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir);
    oStream.writeInt(level.ordinal());
    oStream.close();

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && StdFileTypes.JAVA.equals(child.getFileType())) {
        FileBasedIndex.getInstance().requestReindex(child);
      }
    }
  }
}
