/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author peter
 */
public class CharsetUtil {
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final Map<LanguageFileType, Boolean> ourSupportsCharsetDetection = new ConcurrentFactoryMap<LanguageFileType, Boolean>() {
    @Nullable
    @Override
    protected Boolean create(LanguageFileType fileType) {
      Class<?> ftClass = fileType.getClass();
      String methodName = "extractCharsetFromFileContent";
      Class declaring1 = ReflectionUtil.getMethodDeclaringClass(ftClass, methodName, Project.class, VirtualFile.class, String.class);
      Class declaring2 = ReflectionUtil.getMethodDeclaringClass(ftClass, methodName, Project.class, VirtualFile.class, CharSequence.class);
      return !LanguageFileType.class.equals(declaring1) || !LanguageFileType.class.equals(declaring2);
    }
  };

  public static Charset extractCharsetFromFileContent(@Nullable Project project,
                                                      @Nullable VirtualFile virtualFile,
                                                      @Nullable FileType fileType,
                                                      @NotNull CharSequence text) {
    if (fileType instanceof LanguageFileType &&
        // otherwise the default implementations will always convert CharSequence to String unnecessarily, producing garbage  
        ourSupportsCharsetDetection.get(fileType)) {
      return ((LanguageFileType)fileType).extractCharsetFromFileContent(project, virtualFile, text);
    }
    return null;
  }
}
