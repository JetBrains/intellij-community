// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

/**
 * Additional interface which optionally may be implemented by {@link FileType} to control how files are associated
 * with the IDE in the operating system.
 */
public interface OSFileIdeAssociation {
  enum Mode {
    Unsupported,
    ChooseExtensions,
    AllExtensions
  }

  /**
   * @return One of:
   * <ul>
   *   <li>{@code Mode.Unsupported} - Don't suggest use the IDE to open files of this type.</li>
   *   <li>{@code Mode.ChooseExtensions} - Allow a user to choose extensions of files to be opened with the IDE.</li>
   *   <li>{@code Mode.AllExtensions} - Use all available extensions to associate files with the IDE.</li>
   * </ul>
   */
  default Mode getFileIdeAssociationMode() {
    return Mode.AllExtensions;
  }
}
