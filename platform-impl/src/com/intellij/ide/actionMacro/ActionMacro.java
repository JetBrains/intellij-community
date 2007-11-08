package com.intellij.ide.actionMacro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class ActionMacro implements JDOMExternalizable {
  private String myName;

  private ArrayList<ActionDescriptor> myActions = new ArrayList<ActionDescriptor>();
  @NonNls
  public static final String MACRO_ACTION_PREFIX = "Macro.";
  @NonNls
  private static final String ATTRIBUTE_NAME = "name";
  @NonNls
  private static final String ELEMENT_TYPING = "typing";
  @NonNls
  private static final String ATTRIBUTE_TEXT = "text";
  @NonNls
  private static final String ELEMENT_ACTION = "action";
  @NonNls
  private static final String ATTRIBUTE_ID = "id";


  public ActionMacro() {
  }

  public ActionMacro(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public ActionDescriptor[] getActions() {
    return myActions.toArray(new ActionDescriptor[myActions.size()]);
  }

  public void readExternal(Element macro) throws InvalidDataException {
    setName(macro.getAttributeValue(ATTRIBUTE_NAME));
    List actions = macro.getChildren();
    for (Iterator iterator = actions.iterator(); iterator.hasNext();) {
      Element action = (Element)iterator.next();
      if (ELEMENT_TYPING.equals(action.getName())) {
        myActions.add(new TypedDescriptor(action.getAttributeValue(ATTRIBUTE_TEXT)));
      }
      else if (ELEMENT_ACTION.equals(action.getName())) {
        myActions.add(new IdActionDescriptor(action.getAttributeValue(ATTRIBUTE_ID)));
      }
    }
  }

  public void writeExternal(Element macro) throws WriteExternalException {
    macro.setAttribute(ATTRIBUTE_NAME, myName);
    final ActionDescriptor[] actions = getActions();
    for (int i = 0; i < actions.length; i++) {
      ActionDescriptor action = actions[i];
      Element actionNode;
      if (action instanceof TypedDescriptor) {
        actionNode = new Element(ELEMENT_TYPING);
        actionNode.setAttribute(ATTRIBUTE_TEXT, ((TypedDescriptor)action).getText());
      }
      else {
        actionNode = new Element(ELEMENT_ACTION);
        actionNode.setAttribute(ATTRIBUTE_ID, ((IdActionDescriptor)action).getActionId());
      }
      macro.addContent(actionNode);
    }
  }

  public String toString() {
    return myName;
  }

  protected Object clone() {
    ActionMacro copy = new ActionMacro(myName);
    for (int i = 0; i < myActions.size(); i++) {
      ActionDescriptor action = myActions.get(i);
      copy.myActions.add((ActionDescriptor)action.clone());
    }

    return copy;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ActionMacro)) return false;

    final ActionMacro actionMacro = (ActionMacro)o;

    if (!myActions.equals(actionMacro.myActions)) return false;
    if (!myName.equals(actionMacro.myName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myName.hashCode();
    result = 29 * result + myActions.hashCode();
    return result;
  }

  public void deleteAction(int idx) {
    myActions.remove(idx);
  }

  public void appendAction(String actionId) {
    myActions.add(new IdActionDescriptor(actionId));
  }

  public void appendKeytyped(char c) {
    ActionDescriptor lastAction = myActions.size() > 0 ? myActions.get(myActions.size() - 1) : null;
    if (lastAction instanceof TypedDescriptor) {
      ((TypedDescriptor)lastAction).addChar(c);
    }
    else {
      myActions.add(new TypedDescriptor(c));
    }
  }

  public String getActionId() {
    return MACRO_ACTION_PREFIX + myName;
  }

  public interface ActionDescriptor {
    Object clone();

    void playBack(DataContext context);
  }

  public static class TypedDescriptor implements ActionDescriptor {
    private String myText;

    public TypedDescriptor(String text) {
      myText = text;
    }

    public TypedDescriptor(char c) {
      myText = String.valueOf(c);
    }

    public void addChar(char c) {
      myText = myText + c;
    }

    public String getText() {
      return myText;
    }

    public Object clone() {
      return new TypedDescriptor(myText);
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TypedDescriptor)) return false;
      return myText.equals(((TypedDescriptor)o).myText);
    }

    public int hashCode() {
      return myText.hashCode();
    }

    public String toString() {
      return IdeBundle.message("action.descriptor.typing", myText);
    }

    public void playBack(DataContext context) {
      Editor editor = DataKeys.EDITOR.getData(context);
      final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
      char chars[] = myText.toCharArray();
      for (int i = 0; i < chars.length; i++) {
        typedAction.actionPerformed(editor, chars[i], context);
      }
    }
  }

  public static class IdActionDescriptor implements ActionDescriptor {
    private String actionId;

    public IdActionDescriptor(String id) {
      this.actionId = id;
    }

    public String getActionId() {
      return actionId;
    }

    public String toString() {
      return IdeBundle.message("action.descriptor.action", actionId);
    }

    public Object clone() {
      return new IdActionDescriptor(actionId);
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IdActionDescriptor)) return false;
      return actionId.equals(((IdActionDescriptor)o).actionId);
    }

    public int hashCode() {
      return actionId.hashCode();
    }

    public void playBack(DataContext context) {
      AnAction action = ActionManager.getInstance().getAction(getActionId());
      if (action == null) return;
      Presentation presentation = (Presentation)action.getTemplatePresentation().clone();
      AnActionEvent event = new AnActionEvent(null, context, "MACRO_PLAYBACK", presentation, ActionManager.getInstance(), 0);
      action.beforeActionPerformedUpdate(event);
      if (!presentation.isEnabled()) {
        return;
      }
      action.actionPerformed(event);
    }
  }
}
