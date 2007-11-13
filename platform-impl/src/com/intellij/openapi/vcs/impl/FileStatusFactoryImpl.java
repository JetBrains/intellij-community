package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class FileStatusFactoryImpl implements FileStatusFactory {
  private final List<FileStatus> myStatuses = new ArrayList<FileStatus>();

  public FileStatus createFileStatus(String id, String description, Color color) {
    FileStatusImpl result = new FileStatusImpl(id, ColorKey.createColorKey("FILESTATUS_" + id, color), description);
    myStatuses.add(result);
    return result;
  }

  public FileStatus[] getAllFileStatuses() {
    return myStatuses.toArray(new FileStatus[myStatuses.size()]);
  }
}