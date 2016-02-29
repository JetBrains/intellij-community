/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

public interface FileStatus {
  Color COLOR_NOT_CHANGED = null; // deliberately null, do not use hardcoded Color.BLACK
  Color COLOR_NOT_CHANGED_RECURSIVE = new Color(138, 164, 200);
  Color COLOR_NOT_CHANGED_IMMEDIATE = new Color(50, 100, 180);

  Color COLOR_MERGE = new Color(117, 3, 220);
  Color COLOR_MODIFIED = new Color(0, 50, 160);
  Color COLOR_MISSING = Gray._97;
  Color COLOR_ADDED = new Color(10, 119, 0);
  Color COLOR_OUT_OF_DATE = Color.yellow.darker().darker();
  Color COLOR_HIJACKED = Color.ORANGE.darker();
  Color COLOR_SWITCHED = new Color(8, 151, 143);
  Color COLOR_UNKNOWN = new Color(153, 51, 0);

  FileStatus NOT_CHANGED = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED", VcsBundle.message("file.status.name.up.to.date"), COLOR_NOT_CHANGED);
  FileStatus NOT_CHANGED_IMMEDIATE = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED_IMMEDIATE", VcsBundle.message(
    "file.status.name.up.to.date.immediate.children"), COLOR_NOT_CHANGED_IMMEDIATE);
  FileStatus NOT_CHANGED_RECURSIVE = FileStatusFactory.getInstance().createFileStatus("NOT_CHANGED_RECURSIVE", VcsBundle.message(
    "file.status.name.up.to.date.recursive.children"), COLOR_NOT_CHANGED_RECURSIVE);
  FileStatus DELETED = FileStatusFactory.getInstance().createFileStatus("DELETED", VcsBundle.message("file.status.name.deleted"), COLOR_MISSING);
  FileStatus MODIFIED = FileStatusFactory.getInstance().createFileStatus("MODIFIED", VcsBundle.message("file.status.name.modified"), COLOR_MODIFIED);
  FileStatus ADDED = FileStatusFactory.getInstance().createFileStatus("ADDED", VcsBundle.message("file.status.name.added"), COLOR_ADDED);
  FileStatus MERGE = FileStatusFactory.getInstance().createFileStatus("MERGED", VcsBundle.message("file.status.name.merged"), COLOR_MERGE);
  FileStatus UNKNOWN = FileStatusFactory.getInstance().createFileStatus("UNKNOWN", VcsBundle.message("file.status.name.unknown"), COLOR_UNKNOWN);
  FileStatus IGNORED = FileStatusFactory.getInstance().createFileStatus("IDEA_FILESTATUS_IGNORED", VcsBundle.message("file.status.name.ignored"), new Color(114, 114, 56));
  FileStatus HIJACKED = FileStatusFactory.getInstance().createFileStatus("HIJACKED", VcsBundle.message("file.status.name.hijacked"), COLOR_HIJACKED);
  FileStatus MERGED_WITH_CONFLICTS = FileStatusFactory.getInstance()
   .createFileStatus("IDEA_FILESTATUS_MERGED_WITH_CONFLICTS", VcsBundle.message("file.status.name.merged.with.conflicts"), Color.red);
  FileStatus MERGED_WITH_BOTH_CONFLICTS = FileStatusFactory.getInstance()
   .createFileStatus("IDEA_FILESTATUS_MERGED_WITH_BOTH_CONFLICTS", VcsBundle.message("file.status.name.merged.with.both.conflicts"), Color.red);
  FileStatus MERGED_WITH_PROPERTY_CONFLICTS = FileStatusFactory.getInstance()
   .createFileStatus("IDEA_FILESTATUS_MERGED_WITH_PROPERTY_CONFLICTS", VcsBundle.message("file.status.name.merged.with.property.conflicts"), Color.red);
  FileStatus DELETED_FROM_FS = FileStatusFactory.getInstance()
   .createFileStatus("IDEA_FILESTATUS_DELETED_FROM_FILE_SYSTEM", VcsBundle.message("file.status.name.deleted.from.file.system"),
                     new Color(119, 56, 149));
  FileStatus SWITCHED = FileStatusFactory.getInstance().createFileStatus("SWITCHED", VcsBundle.message("file.status.name.switched"), COLOR_SWITCHED);
  FileStatus OBSOLETE = FileStatusFactory.getInstance().createFileStatus("OBSOLETE", VcsBundle.message("file.status.name.obsolete"), COLOR_OUT_OF_DATE);

  FileStatus SUPPRESSED = FileStatusFactory.getInstance().createFileStatus("SUPPRESSED", VcsBundle.message("file.status.name.suppressed"), COLOR_NOT_CHANGED);

  String getText();

  Color getColor();

  @NotNull
  ColorKey getColorKey();

  @NotNull
  String getId();
}
