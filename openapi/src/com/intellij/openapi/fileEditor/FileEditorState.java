/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

/**
 * This object is used to store/restore editor state between restarts.
 * For example, text editor can store caret position, scroll postion,
 * information about folded regions, etc.
 *
 * @author Vladimir Kondratyev
 */
public interface FileEditorState {
  boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level);
}
