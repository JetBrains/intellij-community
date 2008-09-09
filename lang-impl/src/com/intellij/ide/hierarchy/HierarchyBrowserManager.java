package com.intellij.ide.hierarchy;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class HierarchyBrowserManager implements JDOMExternalizable, ProjectComponent {
  public boolean IS_AUTOSCROLL_TO_SOURCE;
  public boolean SORT_ALPHABETICALLY;
  public boolean HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED;
  private final Project myProject;
  private ContentManager myContentManager;

  public HierarchyBrowserManager(final Project project) {
    myProject = project;
  }

  public final void disposeComponent() {
  }

  public final void initComponent() { }

  public final void projectOpened() {
    final ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(myProject);
    final ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.HIERARCHY, true, ToolWindowAnchor.RIGHT);
    myContentManager = toolWindow.getContentManager();
    toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowHierarchy.png"));
    new ContentManagerWatcher(toolWindow,myContentManager);
  }

  public final void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.HIERARCHY);
    myContentManager = null;
  }

  public final ContentManager getContentManager() {
    return myContentManager;
  }

  public static HierarchyBrowserManager getInstance(final Project project) {
    return project.getComponent(HierarchyBrowserManager.class);
  }

  @NotNull
  public final String getComponentName() {
    return "HierarchyBrowserManager";
  }

  public final void readExternal(final Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public final void writeExternal(final Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
