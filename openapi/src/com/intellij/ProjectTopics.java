/*
 * @author max
 */
package com.intellij;

import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.messages.Topic;
import com.intellij.util.LogicalRootsManager;

public class ProjectTopics {
  public static final Topic<ModuleRootListener> PROJECT_ROOTS = new Topic<ModuleRootListener>("project root changes", ModuleRootListener.class);
  public static final Topic<ModuleListener> MODULES = new Topic<ModuleListener>("modules added or removed from project", ModuleListener.class);
  public static final Topic<FileEditorManagerListener> FILE_EDITOR_MANAGER = new Topic<FileEditorManagerListener>("file editor events", FileEditorManagerListener.class);
  public static final Topic<LogicalRootsManager.LogicalRootListener> LOGICAL_ROOTS = new Topic<LogicalRootsManager.LogicalRootListener>("logical root changes", LogicalRootsManager.LogicalRootListener.class);
}