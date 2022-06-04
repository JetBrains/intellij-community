/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.*;

import java.awt.*;

@SuppressWarnings("UseJBColor")
public interface FileStatus {
  /** @deprecated use {@link FileStatus#getColor} */
  @Deprecated(forRemoval = true)
  Color COLOR_MERGE = new Color(117, 3, 220);
  /** @deprecated use {@link FileStatus#getColor} */
  @Deprecated(forRemoval = true)
  Color COLOR_MODIFIED = new Color(0, 50, 160);
  /** @deprecated use {@link FileStatus#getColor} */
  @Deprecated(forRemoval = true)
  Color COLOR_MISSING = Gray._97;
  /** @deprecated use {@link FileStatus#getColor} */
  @Deprecated(forRemoval = true)
  Color COLOR_OUT_OF_DATE = Color.yellow.darker().darker();
  /** @deprecated use {@link FileStatus#getColor} */
  @Deprecated(forRemoval = true)
  Color COLOR_SWITCHED = new Color(8, 151, 143);
  /** @deprecated use {@link FileStatus#getColor} */
  @Deprecated(forRemoval = true)
  Color COLOR_UNKNOWN = new Color(153, 51, 0);

  FileStatus NOT_CHANGED = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED", PlatformEditorBundle
    .messagePointer("file.status.name.up.to.date"), null);
  FileStatus NOT_CHANGED_IMMEDIATE = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED_IMMEDIATE", PlatformEditorBundle
    .messagePointer("file.status.name.up.to.date.immediate.children"), null);
  FileStatus NOT_CHANGED_RECURSIVE = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED_RECURSIVE", PlatformEditorBundle
    .messagePointer("file.status.name.up.to.date.recursive.children"), null);
  FileStatus DELETED = FileStatusFactory.getInstance().createFileStatus("DELETED", PlatformEditorBundle
    .messagePointer("file.status.name.deleted"), null);
  FileStatus MODIFIED = FileStatusFactory.getInstance().createFileStatus("MODIFIED", PlatformEditorBundle
    .messagePointer("file.status.name.modified"), null);
  FileStatus ADDED = FileStatusFactory.getInstance().createFileStatus("ADDED", PlatformEditorBundle
    .messagePointer("file.status.name.added"), null);
  FileStatus MERGE = FileStatusFactory.getInstance().createFileStatus("MERGED", PlatformEditorBundle
    .messagePointer("file.status.name.merged"), null);
  FileStatus UNKNOWN = FileStatusFactory.getInstance().createFileStatus("UNKNOWN", PlatformEditorBundle
    .messagePointer("file.status.name.unknown"), null);
  FileStatus IGNORED = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_IGNORED", PlatformEditorBundle
    .messagePointer("file.status.name.ignored"), null);
  FileStatus HIJACKED = FileStatusFactory.getInstance().createFileStatus("HIJACKED", PlatformEditorBundle
    .messagePointer("file.status.name.hijacked"), null);
  FileStatus MERGED_WITH_CONFLICTS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_MERGED_WITH_CONFLICTS", PlatformEditorBundle
    .messagePointer("file.status.name.merged.with.conflicts"), null);
  FileStatus MERGED_WITH_BOTH_CONFLICTS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_MERGED_WITH_BOTH_CONFLICTS", PlatformEditorBundle
    .messagePointer("file.status.name.merged.with.both.conflicts"), null);
  FileStatus MERGED_WITH_PROPERTY_CONFLICTS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_MERGED_WITH_PROPERTY_CONFLICTS", PlatformEditorBundle
    .messagePointer("file.status.name.merged.with.property.conflicts"), null);
  FileStatus DELETED_FROM_FS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_DELETED_FROM_FILE_SYSTEM", PlatformEditorBundle
    .messagePointer("file.status.name.deleted.from.file.system"), null);
  FileStatus SWITCHED = FileStatusFactory.getInstance().createFileStatus("SWITCHED", PlatformEditorBundle
    .messagePointer("file.status.name.switched"), null);
  FileStatus OBSOLETE = FileStatusFactory.getInstance().createFileStatus("OBSOLETE", PlatformEditorBundle
    .messagePointer("file.status.name.obsolete"), null);
  FileStatus SUPPRESSED = FileStatusFactory.getInstance().createFileStatus("SUPPRESSED", PlatformEditorBundle
    .messagePointer("file.status.name.suppressed"), null);

  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getText();

  @Nullable
  Color getColor();

  @NotNull
  ColorKey getColorKey();

  @NotNull
  @NonNls
  String getId();
}
