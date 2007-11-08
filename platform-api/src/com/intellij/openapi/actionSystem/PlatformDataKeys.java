package com.intellij.openapi.actionSystem;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.ContentManager;

import java.awt.*;

/**
 * @author yole
 */
@SuppressWarnings({"deprecation"})
public class PlatformDataKeys {
  public static final DataKey<Project> PROJECT = DataKey.create(DataConstants.PROJECT);
  public static final DataKey<Editor> EDITOR = DataKey.create(DataConstants.EDITOR);
  public static final DataKey<Editor> EDITOR_EVEN_IF_INACTIVE = DataKey.create(DataConstants.EDITOR_EVEN_IF_INACTIVE);
  public static final DataKey<Navigatable> NAVIGATABLE = DataKey.create(DataConstants.NAVIGATABLE);
  public static final DataKey<Navigatable[]> NAVIGATABLE_ARRAY = DataKey.create(DataConstants.NAVIGATABLE_ARRAY);
  public static final DataKey<VirtualFile> VIRTUAL_FILE = DataKey.create(DataConstants.VIRTUAL_FILE);
  public static final DataKey<VirtualFile[]> VIRTUAL_FILE_ARRAY = DataKey.create(DataConstants.VIRTUAL_FILE_ARRAY);
  public static final DataKey<FileEditor> FILE_EDITOR = DataKey.create(DataConstants.FILE_EDITOR);
  public static final DataKey<String> FILE_TEXT = DataKey.create(DataConstants.FILE_TEXT);
  public static final DataKey<Boolean> IS_MODAL_CONTEXT = DataKey.create(DataConstants.IS_MODAL_CONTEXT);
  public static final DataKey<DiffViewer> DIFF_VIEWER = DataKey.create(DataConstants.DIFF_VIEWER);
  public static final DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("diffRequest");
  public static final DataKey<String> HELP_ID = DataKey.create(DataConstants.HELP_ID);
  public static final DataKey<Project> PROJECT_CONTEXT = DataKey.create(DataConstants.PROJECT_CONTEXT);
  public static final DataKey<Component> CONTEXT_COMPONENT = DataKey.create(DataConstants.CONTEXT_COMPONENT);
  public static final DataKey<CopyProvider> COPY_PROVIDER = DataKey.create(DataConstants.COPY_PROVIDER);
  public static final DataKey<CutProvider> CUT_PROVIDER = DataKey.create(DataConstants.CUT_PROVIDER);
  public static final DataKey<PasteProvider> PASTE_PROVIDER = DataKey.create(DataConstants.PASTE_PROVIDER);
  public static final DataKey<DeleteProvider> DELETE_ELEMENT_PROVIDER = DataKey.create(DataConstants.DELETE_ELEMENT_PROVIDER);
  public static final DataKey<ContentManager> CONTENT_MANAGER = DataKey.create(DataConstantsEx.CONTENT_MANAGER);
  public static final DataKey<ToolWindow> TOOL_WINDOW = DataKey.create("TOOL_WINDOW");
}