/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.keymap.KeymapUtil;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Represents an entity that has a state, a presentation and can be performed.
 *
 * For an action to be useful, you need to implement {@link AnAction#actionPerformed}
 * and optionally to override {@link com.intellij.openapi.actionSystem.AnAction#update}. By overriding the
 * {@link com.intellij.openapi.actionSystem.AnAction#update} method you can dynamically change action's presentation
 * depending on the place (for more information on places see {@link ActionPlaces}.
 *
 * The same action can have various presentations.
 *
 * <pre>
 *  public class MyAction extends AnAction {
 *    public MyAction() {
 *      // ...
 *    }
 *
 *    public void update(AnActionEvent e) {
 *      Presentation presentation = e.getPresentation();
 *      if (e.getPlace().equals(ActionPlaces.MAIN_MENU)) {
 *        presentation.setText("My Menu item name");
 *      } else if (e.getPlace().equals(ActionPlaces.MAIN_TOOLBAR)) {
 *        presentation.setText("My Toolbar item name");
 *      }
 *    }
 *
 *    public void actionPerformed(AnActionEvent e) { ... }
 *  }
 * </pre>
 *
 * @see AnActionEvent
 * @see Presentation
 * @see ActionPlaces
 */
public abstract class AnAction {
  public static final AnAction[] EMPTY_ARRAY = new AnAction[0];
  public static final String ourClientProperty = "AnAction.shortcutSet";

  private Presentation myTemplatePresentation;
  private ShortcutSet myShortcutSet;
  private boolean myEnabledInModalContext;


  private static final ShortcutSet ourEmptyShortcutSet = new CustomShortcutSet(new Shortcut[0]);

  /**
   * Creates a new action with its text, description and icon set to <code>null</code>.
   */
  public AnAction(){
    this(null);
  }

  /**
   * Creates a new action with the specified text. Description and icon are
   * set to <code>null</code>.
   *
   * @param text Serves as a tooltip when the presention is a button and the name of the
   *  menu item when the presentation is a menu item.
   */
  public AnAction(String text){
    this(text, null, null);
  }

  /**
   * Constructs a new action with the specified text, description and icon.
   *
   * @param text Serves as a tooltip when the presention is a button and the name of the
   *  menu item when the presentation is a menu item
   *
   * @param description Describes current action, this description will appear on
   *  the status bar when presentation has focus
   *
   * @param icon Action's icon
   */
  public AnAction(String text, String description, Icon icon){
    myShortcutSet = ourEmptyShortcutSet;
    myEnabledInModalContext = false;
    Presentation presentation = getTemplatePresentation();
    presentation.setText(text);
    presentation.setDescription(description);
    presentation.setIcon(icon);
  }

  /**
   * Returns the shortcut set associated with this action.
   *
   * @return shorcut set associated with this action
   */
  public final ShortcutSet getShortcutSet(){
    return myShortcutSet;
  }

  /**
   * Registers a set of shortcuts that will be processed when the specified component
   * is the ancestor of focused component. Note that the action doesn't have
   * to be registered in action manager in order for that shorcut to work.
   *
   * @param shortcutSet
   * @param component
   */
  public final void registerCustomShortcutSet(ShortcutSet shortcutSet, JComponent component){
    myShortcutSet = shortcutSet;
    if (component != null){
      ArrayList actionList = (ArrayList)component.getClientProperty(ourClientProperty);
      if (actionList == null){
        actionList = new ArrayList(1);
        component.putClientProperty(ourClientProperty, actionList);
      }
      if (!actionList.contains(this)){
        actionList.add(this);
      }
    }
  }

  /**
   * Copies template presentation and shortcuts set from <code>sourceAction</code>.
   *
   * @param sourceAction cannot be <code>null</code>
   */
  public final void copyFrom(AnAction sourceAction){
    if (sourceAction == null) {
      throw new IllegalArgumentException("sourceAction cannot be null");
    }
    Presentation sourcePresentation = sourceAction.getTemplatePresentation();
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(sourcePresentation.getIcon());
    presentation.setText(sourcePresentation.getText());
    presentation.setDescription(sourcePresentation.getDescription());
    myShortcutSet = sourceAction.myShortcutSet;
  }



  public final boolean isEnabledInModalContext() {
    return myEnabledInModalContext;
  }

  protected final void setEnabledInModalContext(boolean enabledInModalContext) {
    myEnabledInModalContext = enabledInModalContext;
  }

  /**
   * Override with true returned if your action has to display its text along with the icon when placed in the toolbar
   */
  public boolean displayTextInToolbar() {
    return false;
  }

  /**
   * Updates the state of the action. Default implementation does nothing.
   * Override this method to provide the ability to dynamically change action's
   * state and(or) presentation depending on the context (For example
   * when your action state depends on the selection you can check for
   * selection and change the state accordingly).
   *
   * @param e Carries information on the invocation place and data available
   */
  public void update(AnActionEvent e){
  }

  /**
   * Returns a template presentation that will be used
   * as a template for created presentations.
   *
   * @return template presentation
   */
  public final Presentation getTemplatePresentation(){
    if (myTemplatePresentation == null){
      myTemplatePresentation = new Presentation();
    }
    return myTemplatePresentation;
  }

  /**
   * Implement this method to provide your action handler.
   *
   * @param e Carries information on the invocation place
   */
  public abstract void actionPerformed(AnActionEvent e);

  protected void setShortcutSet(ShortcutSet shortcutSet) {
    myShortcutSet = shortcutSet;
  }

  public static String createTooltipText(String s, AnAction action) {
    String toolTipText = s != null ? s : "";
    while (StringUtil.endsWithChar(toolTipText, '.')) {
      toolTipText = toolTipText.substring(0, toolTipText.length() - 1);
    }
    String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
    if (shortcutsText.length() > 0) {
      toolTipText += " (" + shortcutsText + ")";
    }
    return toolTipText;
  }
}
