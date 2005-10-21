package com.intellij.ide.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ArrayUtil;

public class PreviousTabAction extends TabNavigationActionBase {
  public PreviousTabAction () { super (-1); }
}
