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
    return new ContentManagerImpl(contentUI, canCloseContents, project);
  }

  public ContentManager createContentManager(boolean canCloseContents, Project project) {
    return new ContentManagerImpl(new TabbedPaneContentUI(), canCloseContents, project);
  }
}
