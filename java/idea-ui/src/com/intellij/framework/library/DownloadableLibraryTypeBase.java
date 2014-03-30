/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.framework.library;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;

/**
 * @deprecated use {@link DownloadableLibraryType} instead
 */
public abstract class DownloadableLibraryTypeBase extends DownloadableLibraryType {
  protected DownloadableLibraryTypeBase(@NotNull String libraryCategoryName,
                                        @NotNull String libraryTypeId,
                                        @NotNull String groupId,
                                        @NotNull Icon icon,
                                        @NotNull URL... localUrls) {
    super(libraryCategoryName, libraryTypeId, groupId, icon, localUrls);
  }
}
