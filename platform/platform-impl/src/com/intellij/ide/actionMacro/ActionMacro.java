// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionMacro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.ui.playback.commands.KeyCodeTypeCommand;
import com.intellij.openapi.ui.playback.commands.TypeCommand;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ActionMacro {
  private String myName;

  private final ArrayList<ActionDescriptor> myActions = new ArrayList<>();
  @NonNls
  public static final String MACRO_ACTION_PREFIX = "Macro.";
  @NonNls
  private static final String ATTRIBUTE_NAME = "name";
  @NonNls
  private static final String ELEMENT_TYPING = "typing";

  @NonNls
  private static final String ELEMENT_SHORTCUT = "shortuct";
  @NonNls
  private static final String ATTRIBUTE_TEXT = "text";
  @NonNls
  private static final String ATTRIBUTE_KEY_CODES = "text-keycode";
  @NonNls
  private static final String ELEMENT_ACTION = "action";
  @NonNls
  private static final String ATTRIBUTE_ID = "id";


  public ActionMacro() {
  }

  public ActionMacro(String name) {
    myName = name;
  }

  @NlsSafe
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public ActionDescriptor[] getActions() {
    return myActions.toArray(new ActionDescriptor[0]);
  }

  public void readExternal(Element macro) {
    setName(macro.getAttributeValue(ATTRIBUTE_NAME));
    List actions = macro.getChildren();
    for (final Object o : actions) {
      Element action = (Element)o;
      if (ELEMENT_TYPING.equals(action.getName())) {
        Couple<List<Integer>> codes = parseKeyCodes(action.getAttributeValue(ATTRIBUTE_KEY_CODES));

        String text = action.getText();
        if (text == null || text.length() == 0) {
          text = action.getAttributeValue(ATTRIBUTE_TEXT);
        }
        text = text.replaceAll("&#x20;", " ");

        if (!StringUtil.isEmpty(text)) {
          myActions.add(new TypedDescriptor(text, codes.getFirst(), codes.getSecond()));
        }
      }
      else if (ELEMENT_ACTION.equals(action.getName())) {
        myActions.add(new IdActionDescriptor(action.getAttributeValue(ATTRIBUTE_ID)));
      }
      else if (ELEMENT_SHORTCUT.equals(action.getName())) {
        myActions.add(new ShortcutActionDesciption(action.getAttributeValue(ATTRIBUTE_TEXT)));
      }
    }
  }

  private static Couple<List<Integer>> parseKeyCodes(String keyCodesText) {
    return KeyCodeTypeCommand.parseKeyCodes(keyCodesText);
  }

  public static String unparseKeyCodes(Couple<List<Integer>> keyCodes) {
    return KeyCodeTypeCommand.unparseKeyCodes(keyCodes);
  }

  public void writeExternal(Element macro)  {
    macro.setAttribute(ATTRIBUTE_NAME, myName);
    final ActionDescriptor[] actions = getActions();
    for (ActionDescriptor action : actions) {
      Element actionNode = null;
      if (action instanceof TypedDescriptor) {
        actionNode = new Element(ELEMENT_TYPING);
        TypedDescriptor typedDescriptor = (TypedDescriptor)action;
        actionNode.setText(typedDescriptor.getText().replaceAll(" ", "&#x20;"));
        actionNode.setAttribute(ATTRIBUTE_KEY_CODES, unparseKeyCodes(
          Couple.of(typedDescriptor.getKeyCodes(), typedDescriptor.getKeyModifiers())));
      }
      else if (action instanceof IdActionDescriptor) {
        actionNode = new Element(ELEMENT_ACTION);
        actionNode.setAttribute(ATTRIBUTE_ID, ((IdActionDescriptor)action).getActionId());
      }
      else if (action instanceof ShortcutActionDesciption) {
        actionNode = new Element(ELEMENT_SHORTCUT);
        actionNode.setAttribute(ATTRIBUTE_TEXT, ((ShortcutActionDesciption)action).getText());
      }


      assert actionNode != null : action;

      macro.addContent(actionNode);
    }
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  protected Object clone() {
    ActionMacro copy = new ActionMacro(myName);
    for (int i = 0; i < myActions.size(); i++) {
      ActionDescriptor action = myActions.get(i);
      copy.myActions.add((ActionDescriptor)action.clone());
    }

    return copy;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ActionMacro)) return false;

    final ActionMacro actionMacro = (ActionMacro)o;

    if (!myActions.equals(actionMacro.myActions)) return false;
    if (!myName.equals(actionMacro.myName)) return false;

    return true;
  }

  @Override
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

  public void appendShortcut(String text) {
    myActions.add(new ShortcutActionDesciption(text));
  }

  public void appendKeytyped(char c, int keyCode, @JdkConstants.InputEventMask int modifiers) {
    ActionDescriptor lastAction = myActions.size() > 0 ? myActions.get(myActions.size() - 1) : null;
    if (lastAction instanceof TypedDescriptor) {
      ((TypedDescriptor)lastAction).addChar(c, keyCode, modifiers);
    }
    else {
      myActions.add(new TypedDescriptor(c, keyCode, modifiers));
    }
  }

  public String getActionId() {
    return MACRO_ACTION_PREFIX + myName;
  }

  public interface ActionDescriptor {
    Object clone();

    void playBack(DataContext context);

    void generateTo(StringBuffer script);
  }

  public static class TypedDescriptor implements ActionDescriptor {

    private String myText;

    private final List<Integer> myKeyCodes = new ArrayList<>();
    private final List<Integer> myModifiers = new ArrayList<>();

    public TypedDescriptor(@NotNull String text, List<Integer> keyCodes, List<Integer> modifiers) {
      myText = text;
      myKeyCodes.addAll(keyCodes);
      myModifiers.addAll(modifiers);

      assert myKeyCodes.size() == myModifiers.size() : "codes=" + myKeyCodes.toString() + " modifiers=" + myModifiers.toString();
    }

    public TypedDescriptor(char c, int keyCode, @JdkConstants.InputEventMask int modifiers) {
      myText = String.valueOf(c);
      myKeyCodes.add(keyCode);
      myModifiers.add(modifiers);
    }

    public void addChar(char c, int keyCode, @JdkConstants.InputEventMask int modifier) {
      myText += c;
      myKeyCodes.add(keyCode);
      myModifiers.add(modifier);
    }

    public String getText() {
      return myText;
    }

    @Override
    public Object clone() {
      return new TypedDescriptor(myText, myKeyCodes, myModifiers);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TypedDescriptor)) return false;
      return myText.equals(((TypedDescriptor)o).myText);
    }

    @Override
    public int hashCode() {
      return myText.hashCode();
    }

    @Override
    public void generateTo(StringBuffer script) {
      if (TypeCommand.containsUnicode(myText)) {
        script.append(KeyCodeTypeCommand.PREFIX).append(" ");

        for (int i = 0; i < myKeyCodes.size(); i++) {
          Integer each = myKeyCodes.get(i);
          script.append(each.toString());
          script.append(KeyCodeTypeCommand.MODIFIER_DELIMITER);
          script.append(myModifiers.get(i));
          if (i < myKeyCodes.size() - 1) {
            script.append(KeyCodeTypeCommand.CODE_DELIMITER);
          }
        }
        script.append(" ").append(myText).append("\n");
      }
      else {
        script.append(myText);
        script.append("\n");
      }
    }

    @Override
    public String toString() {
      return IdeBundle.message("action.descriptor.typing", myText);
    }

    @Override
    public void playBack(DataContext context) {
      Editor editor = CommonDataKeys.EDITOR.getData(context);
      EditorActionManager.getInstance();
      final TypedAction typedAction = TypedAction.getInstance();
      for (final char aChar : myText.toCharArray()) {
        typedAction.actionPerformed(editor, aChar, context);
      }
    }

    public List<Integer> getKeyCodes() {
      return myKeyCodes;
    }

    public List<Integer> getKeyModifiers() {
      return myModifiers;
    }
  }

  public static class ShortcutActionDesciption implements ActionDescriptor {

    private final String myKeyStroke;

    public ShortcutActionDesciption(String stroke) {
      myKeyStroke = stroke;
    }

    @Override
    public Object clone() {
      return new ShortcutActionDesciption(myKeyStroke);
    }

    @Override
    public void playBack(DataContext context) {
    }

    @Override
    public void generateTo(StringBuffer script) {
      script.append("%[").append(myKeyStroke).append("]\n");
    }

    @Override
    public String toString() {
      return IdeBundle.message("action.descriptor.keystroke", myKeyStroke);
    }

    public String getText() {
      return myKeyStroke;
    }
  }

  public static class IdActionDescriptor implements ActionDescriptor {
    private final String actionId;

    public IdActionDescriptor(String id) {
      this.actionId = id;
    }

    public String getActionId() {
      return actionId;
    }

    @Override
    public String toString() {
      return IdeBundle.message("action.descriptor.action", actionId);
    }

    @Override
    public Object clone() {
      return new IdActionDescriptor(actionId);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IdActionDescriptor)) return false;
      return actionId.equals(((IdActionDescriptor)o).actionId);
    }

    @Override
    public int hashCode() {
      return actionId.hashCode();
    }

    @Override
    public void playBack(DataContext context) {
      AnAction action = ActionManager.getInstance().getAction(getActionId());
      if (action == null) return;
      Presentation presentation = action.getTemplatePresentation().clone();
      AnActionEvent event = new AnActionEvent(null, context, "MACRO_PLAYBACK", presentation, ActionManager.getInstance(), 0);
      action.beforeActionPerformedUpdate(event);
      if (!presentation.isEnabled()) {
        return;
      }
      action.actionPerformed(event);
    }

    @Override
    public void generateTo(StringBuffer script) {
      script.append("%action ").append(getActionId()).append("\n");
    }
  }
}
