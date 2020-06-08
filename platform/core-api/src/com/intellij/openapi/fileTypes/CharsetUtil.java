// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public final class CharsetUtil {
  private static final Map<String, Boolean> ourSupportsCharsetDetection = new ConcurrentHashMap<>();

  private static boolean overridesExtractCharsetFromContent(LanguageFileType fileType) {
    Class<?> ftClass = fileType.getClass();
    String methodName = "extractCharsetFromFileContent";
    Class<?> declaring1 = ReflectionUtil.getMethodDeclaringClass(ftClass, methodName, Project.class, VirtualFile.class, String.class);
    Class<?> declaring2 = ReflectionUtil.getMethodDeclaringClass(ftClass, methodName, Project.class, VirtualFile.class, CharSequence.class);
    return !LanguageFileType.class.equals(declaring1) || !LanguageFileType.class.equals(declaring2);
  }

  public static Charset extractCharsetFromFileContent(@Nullable Project project,
                                                      @Nullable VirtualFile virtualFile,
                                                      @Nullable FileType fileType,
                                                      @NotNull CharSequence text) {
    if (fileType instanceof LanguageFileType &&
        // otherwise the default implementations will always convert CharSequence to String unnecessarily, producing garbage
        ourSupportsCharsetDetection.computeIfAbsent(fileType.getName(),
                                                    __ -> overridesExtractCharsetFromContent((LanguageFileType)fileType))) {
      return ((LanguageFileType)fileType).extractCharsetFromFileContent(project, virtualFile, text);
    }
    return null;
  }
}
