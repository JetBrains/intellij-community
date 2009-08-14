package com.intellij.ui.content;

import com.intellij.ui.content.impl.ContentImpl;
import com.intellij.ui.content.impl.ContentManagerImpl;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public class ContentFactoryImpl implements ContentFactory {
  public ContentImpl createContent(JComponent component, String displayName, boolean isLockable) {
    return new ContentImpl(component, displayName, isLockable);
  }

  public ContentManagerImpl createContentManager(ContentUI contentUI, boolean canCloseContents, Project project) {
    return createContentManager(contentUI, canCloseContents, project, false);
  }

  public ContentManagerImpl createContentManager(ContentUI contentUI, boolean canCloseContents, Project project, boolean dumbAware) {
    return new ContentManagerImpl(contentUI, canCloseContents, project, dumbAware);
  }

  public ContentManager createContentManager(boolean canCloseContents, Project project) {
    return new ContentManagerImpl(new TabbedPaneContentUI(), canCloseContents, project, false);
  }
}
