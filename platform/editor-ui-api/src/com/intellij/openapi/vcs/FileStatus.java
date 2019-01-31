/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@SuppressWarnings("UseJBColor")
public interface FileStatus {
  @Deprecated Color COLOR_MERGE = new Color(117, 3, 220);
  @Deprecated Color COLOR_MODIFIED = new Color(0, 50, 160);
  @Deprecated Color COLOR_MISSING = Gray._97;
  @Deprecated Color COLOR_ADDED = new Color(10, 119, 0);
  @Deprecated Color COLOR_OUT_OF_DATE = Color.yellow.darker().darker();
  @Deprecated Color COLOR_SWITCHED = new Color(8, 151, 143);
  @Deprecated Color COLOR_UNKNOWN = new Color(153, 51, 0);

  FileStatus NOT_CHANGED = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED", VcsBundle.message("file.status.name.up.to.date"));
  FileStatus NOT_CHANGED_IMMEDIATE = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED_IMMEDIATE", VcsBundle.message("file.status.name.up.to.date.immediate.children"));
  FileStatus NOT_CHANGED_RECURSIVE = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED_RECURSIVE", VcsBundle.message("file.status.name.up.to.date.recursive.children"));
  FileStatus DELETED = FileStatusFactory.getInstance().createFileStatus("DELETED", VcsBundle.message("file.status.name.deleted"));
  FileStatus MODIFIED = FileStatusFactory.getInstance().createFileStatus("MODIFIED", VcsBundle.message("file.status.name.modified"));
  FileStatus ADDED = FileStatusFactory.getInstance().createFileStatus("ADDED", VcsBundle.message("file.status.name.added"));
  FileStatus MERGE = FileStatusFactory.getInstance().createFileStatus("MERGED", VcsBundle.message("file.status.name.merged"));
  FileStatus UNKNOWN = FileStatusFactory.getInstance().createFileStatus("UNKNOWN", VcsBundle.message("file.status.name.unknown"));
  FileStatus IGNORED = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_IGNORED", VcsBundle.message("file.status.name.ignored"));
  FileStatus HIJACKED = FileStatusFactory.getInstance().createFileStatus("HIJACKED", VcsBundle.message("file.status.name.hijacked"));
  FileStatus MERGED_WITH_CONFLICTS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_MERGED_WITH_CONFLICTS", VcsBundle.message("file.status.name.merged.with.conflicts"));
  FileStatus MERGED_WITH_BOTH_CONFLICTS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_MERGED_WITH_BOTH_CONFLICTS", VcsBundle.message("file.status.name.merged.with.both.conflicts"));
  FileStatus MERGED_WITH_PROPERTY_CONFLICTS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_MERGED_WITH_PROPERTY_CONFLICTS", VcsBundle.message("file.status.name.merged.with.property.conflicts"));
  FileStatus DELETED_FROM_FS = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_DELETED_FROM_FILE_SYSTEM", VcsBundle.message("file.status.name.deleted.from.file.system"));
  FileStatus SWITCHED = FileStatusFactory.getInstance().createFileStatus("SWITCHED", VcsBundle.message("file.status.name.switched"));
  FileStatus OBSOLETE = FileStatusFactory.getInstance().createFileStatus("OBSOLETE", VcsBundle.message("file.status.name.obsolete"));
  FileStatus SUPPRESSED = FileStatusFactory.getInstance().createFileStatus("SUPPRESSED", VcsBundle.message("file.status.name.suppressed"));

  String getText();

  Color getColor();

  @NotNull
  ColorKey getColorKey();

  @NotNull
  String getId();
}
