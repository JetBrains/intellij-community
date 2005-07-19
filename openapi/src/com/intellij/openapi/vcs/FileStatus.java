/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.peer.PeerFactory;

import java.awt.*;

public interface FileStatus extends NamedComponent {
  Color COLOR_NOT_CHANGED = null; // deliberately null, do not use hardcoded Color.BLACK
  Color COLOR_MERGE = new Color(117, 3, 220);
  Color COLOR_MODIFIED = new Color(0, 50, 160);
  Color COLOR_MISSING = new Color(97, 97, 97);
  Color COLOR_ADDED = new Color(10, 119, 0);
  Color COLOR_OUT_OF_DATE = Color.yellow.darker().darker();
  Color COLOR_UNKNOWN = new Color(153, 51, 0);

  FileStatus NOT_CHANGED = PeerFactory.getInstance().getFileStatusFactory().createFileStatus("NOT_CHANGED", "Up to date", COLOR_NOT_CHANGED);
  FileStatus DELETED = PeerFactory.getInstance().getFileStatusFactory().createFileStatus("DELETED", "Deleted", COLOR_MISSING);
  FileStatus MODIFIED = PeerFactory.getInstance().getFileStatusFactory().createFileStatus("MODIFIED", "Modified", COLOR_MODIFIED);
  FileStatus ADDED = PeerFactory.getInstance().getFileStatusFactory().createFileStatus("ADDED", "Added", COLOR_ADDED);
  FileStatus MERGE = PeerFactory.getInstance().getFileStatusFactory().createFileStatus("MERGED", "Merged", COLOR_MERGE);
  FileStatus UNKNOWN = PeerFactory.getInstance().getFileStatusFactory().createFileStatus("UNKNOWN", "Unknown", COLOR_UNKNOWN);
  FileStatus IGNORED = PeerFactory.getInstance().getFileStatusFactory().createFileStatus("IDEA_FILESTATUS_IGNORED", "Ignored", new Color(114, 114, 56));
  FileStatus MERGED_WITH_CONFLICTS = PeerFactory.getInstance().getFileStatusFactory()
   .createFileStatus("IDEA_FILESTATUS_MERGED_WITH_CONFLICTS", "Merged with conflicts", Color.red);
  FileStatus DELETED_FROM_FS = PeerFactory.getInstance().getFileStatusFactory()
   .createFileStatus("IDEA_FILESTATUS_DELETED_FROM_FILE_SYSTEM", "Deleted from file system",
                     new Color(119, 56, 149));

  Color getColor();

  ColorKey getColorKey();
}
