/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.diagnostic.Logger;

/**
 * The main (and single) purpose of this class is provide lazy initialization
 * of the actions. ClassLoader eats a lot of time on startup to load the actions' classes.
 *
 * @author Vladimir Kondratyev
 */
public class ActionStub extends AnAction{
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.actionSystem.ActionStub");

  private final String myClassName;
  private final String myId;
  private final String myText;
  private final ClassLoader myLoader;

  public ActionStub(String actionClass, String id, String text, ClassLoader loader){
    myLoader = loader;
    LOG.assertTrue(actionClass!=null);
    myClassName=actionClass;
    LOG.assertTrue(id!=null && id.length()>0);
    myId=id;
    LOG.assertTrue(text!=null);
    myText=text;
  }

  public String getClassName(){
    return myClassName;
  }

  public String getId(){
    return myId;
  }

  public String getText(){
    return myText;
  }

  public ClassLoader getLoader() {
    return myLoader;
  }

  public void actionPerformed(AnActionEvent e){
    throw new UnsupportedOperationException();
  }

  /**
   * Copies template presentation and shortcuts set to <code>targetAction</code>.
   *
   * @param targetAction cannot be <code>null</code>
   */
  public final void initAction(AnAction targetAction){
    if (targetAction == null) {
      throw new IllegalArgumentException("targetAction cannot be null");
    }
    Presentation sourcePresentation = getTemplatePresentation();
    Presentation targetPresentation = targetAction.getTemplatePresentation();
    if (targetPresentation.getIcon() == null && sourcePresentation.getIcon() != null) {
      targetPresentation.setIcon(sourcePresentation.getIcon());
    }
    if (targetPresentation.getText() == null && sourcePresentation.getText() != null) {
      targetPresentation.setText(sourcePresentation.getText());
    }
    if (targetPresentation.getDescription() == null && sourcePresentation.getDescription() != null) {
      targetPresentation.setDescription(sourcePresentation.getDescription());
    }
    targetAction.setShortcutSet(getShortcutSet());
  }

}
