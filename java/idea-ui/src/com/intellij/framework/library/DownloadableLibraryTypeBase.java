package com.intellij.framework.library;

import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.URL;
import java.util.List;

public abstract class DownloadableLibraryTypeBase extends DownloadableLibraryType {
  private final Icon myIcon;

  protected DownloadableLibraryTypeBase(@NotNull String libraryCategoryName,
                                        @NotNull String libraryTypeId,
                                        @NotNull String groupId,
                                        @NotNull Icon icon,
                                        @NotNull URL... localUrls) {
    super(new LibraryKind<LibraryVersionProperties>(libraryTypeId), libraryCategoryName,
          DownloadableLibraryService.getInstance().createLibraryDescription(groupId, localUrls));
    myIcon = icon;
  }

  public Icon getIcon() {
    return myIcon;
  }

  protected abstract String[] getDetectionClassNames();

  @Override
  public LibraryVersionProperties detect(@NotNull List<VirtualFile> classesRoots) {
    for (String className : getDetectionClassNames()) {

      final LibraryVersionProperties versionProperties = detectJsfVersion(classesRoots, className);
      if (versionProperties != null) return versionProperties;
    }
    return null;
  }

  @Nullable
  private static LibraryVersionProperties detectJsfVersion(List<VirtualFile> classesRoots, String detectionClass) {
    if (!LibraryUtil.isClassAvailableInLibrary(classesRoots, detectionClass)) {
      return null;
    }
    final String version = JarVersionDetectionUtil.detectJarVersion(detectionClass, classesRoots);
    return new LibraryVersionProperties(version);
  }
}
