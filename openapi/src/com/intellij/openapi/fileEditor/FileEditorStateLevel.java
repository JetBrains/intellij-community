/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

/**
 * @author Vladimir Kondratyev
 */
public final class FileEditorStateLevel {
  public static final FileEditorStateLevel FULL = new FileEditorStateLevel("full");
  public static final FileEditorStateLevel UNDO = new FileEditorStateLevel("undo");
  public static final FileEditorStateLevel NAVIGATION = new FileEditorStateLevel("navigation");

  private final String myText;

  private FileEditorStateLevel(final String text) {
    myText = text;
  }

  public String toString() {
    return "FileEditorStateLevel["+myText+"]";
  }
}
