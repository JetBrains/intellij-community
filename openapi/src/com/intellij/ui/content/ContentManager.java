package com.intellij.ui.content;

import javax.swing.*;

public interface ContentManager {
  boolean canCloseContents();

  JComponent getComponent();

  void addContent  (Content content);
  // [Valentin] Q: throw exception when failed?
  boolean removeContent(Content content);

  void setSelectedContent(Content content);
  Content getSelectedContent();


  void removeAllContents();

  int getContentCount();

  Content[] getContents();

  //TODO[anton,vova] is this method needed?
  Content findContent(String displayName);

  Content getContent(int index);

  Content getContent(JComponent component);

  int getIndexOfContent(Content content);



  void selectPreviousContent();

  void selectNextContent();

  void addContentManagerListener(ContentManagerListener l);

  void removeContentManagerListener(ContentManagerListener l);
}
