/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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

  Color getColor();

  ColorKey getColorKey();
}
