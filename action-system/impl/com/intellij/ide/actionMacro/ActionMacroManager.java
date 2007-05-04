package com.intellij.ide.actionMacro;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class ActionMacroManager implements ExportableApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actionMacro.ActionMacroManager");

  private boolean myIsRecording;
  private ActionManagerEx myActionManager;
  private ActionMacro myLastMacro;
  private ActionMacro myRecordingMacro;
  private ArrayList<ActionMacro> myMacros = new ArrayList<ActionMacro>();
  private String myLastMacroName = null;
  private boolean myIsPlaying = false;
  @NonNls
  private static final String ELEMENT_MACRO = "macro";

  public ActionMacroManager(ActionManagerEx actionManagerEx) {
    myActionManager = actionManagerEx;
    myActionManager.addAnActionListener(new AnActionListener() {
      public void beforeActionPerformed(AnAction action, DataContext dataContext) {
        if (myIsRecording) {
          String id = myActionManager.getId(action);
          //noinspection HardCodedStringLiteral
          if (id != null && !"StartStopMacroRecording".equals(id)) {
            myRecordingMacro.appendAction(id);
          }
        }
      }

      public void beforeEditorTyping(char c, DataContext dataContext) {
        if (myIsRecording) {
          myRecordingMacro.appendKeytyped(c);
        }
      }

      public void afterActionPerformed(final AnAction action, final DataContext dataContext) {
      }
    });
  }

  public void readExternal(Element element) throws InvalidDataException {
    myMacros = new ArrayList<ActionMacro>();
    final List macros = element.getChildren(ELEMENT_MACRO);
    for (Iterator iterator = macros.iterator(); iterator.hasNext();) {
      Element macroElement = (Element)iterator.next();
      ActionMacro macro = new ActionMacro();
      macro.readExternal(macroElement);
      myMacros.add(macro);
    }

    registerActions();
  }

  public String getExternalFileName() {
    return "macros";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("title.macros");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (int i = 0; i < myMacros.size(); i++) {
      ActionMacro macro = myMacros.get(i);
      Element macroElement = new Element(ELEMENT_MACRO);
      macro.writeExternal(macroElement);
      element.addContent(macroElement);
    }
  }

  public static ActionMacroManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ActionMacroManager.class);
  }

  public String getComponentName() {
    return "ActionMacroManager";
  }

  public void initComponent() { }

  public void startRecording(String macroName) {
    LOG.assertTrue(!myIsRecording);
    myIsRecording = true;
    myRecordingMacro = new ActionMacro(macroName);
  }

  public void stopRecording(Project project) {
    LOG.assertTrue(myIsRecording);
    myIsRecording = false;
    String macroName;
    do {
      macroName = Messages.showInputDialog(project,
                                           IdeBundle.message("prompt.enter.macro.name"),
                                           IdeBundle.message("title.enter.macro.name"),
                                           Messages.getQuestionIcon());
      if (macroName == null) {
        myRecordingMacro = null;
        return;
      }

      if ("".equals(macroName)) macroName = null;
    }
    while (macroName != null && !checkCanCreateMacro(macroName));

    myLastMacro = myRecordingMacro;
    addRecordedMacroWithName(macroName);
    registerActions();
  }

  private void addRecordedMacroWithName(String macroName) {
    if (macroName != null) {
      myRecordingMacro.setName(macroName);
      myMacros.add(myRecordingMacro);
      myRecordingMacro = null;
    }
    else {
      for (int i = 0; i < myMacros.size(); i++) {
        ActionMacro macro = myMacros.get(i);
        if (IdeBundle.message("macro.noname").equals(macro.getName())) {
          myMacros.set(i, myRecordingMacro);
          myRecordingMacro = null;
          break;
        }
      }
      if (myRecordingMacro != null) {
        myMacros.add(myRecordingMacro);
        myRecordingMacro = null;
      }
    }
  }

  public void playbackLastMacro() {
    if (myLastMacro != null) {
      playbackMacro(myLastMacro);
    }
  }

  private void playbackMacro(ActionMacro macro) {
    myIsPlaying = true;
    try {
      ActionMacro.ActionDescriptor[] actions = macro.getActions();
      for (int i = 0; i < actions.length; i++) {
        // Right thing here. If some macro changes the context (like transferes focus) we should use changed one.
        actions[i].playBack(DataManager.getInstance().getDataContext());
        IdeEventQueue.getInstance().flushQueue();
      }
      myLastMacro = macro;
    }
    finally{
      myIsPlaying = false;
    }
  }

  public boolean isRecording() {
    return myIsRecording;
  }

  public void disposeComponent() {
  }

  public ActionMacro[] getAllMacros() {
    return myMacros.toArray(new ActionMacro[myMacros.size()]);
  }

  public void removeAllMacros() {
    if (myLastMacro != null) {
      myLastMacroName = myLastMacro.getName();
      myLastMacro = null;
    }
    myMacros = new ArrayList<ActionMacro>();
  }

  public void addMacro(ActionMacro macro) {
    myMacros.add(macro);
    if (myLastMacroName != null && myLastMacroName.equals(macro.getName())) {
      myLastMacro = macro;
      myLastMacroName = null;
    }
  }

  public void playMacro(ActionMacro macro) {
    playbackMacro(macro);
    myLastMacro = macro;
  }

  public boolean hasRecentMacro() {
    return myLastMacro != null;
  }

  public void registerActions() {
    unregisterActions();
    HashSet<String> registeredIds = new HashSet<String>(); // to prevent exception if 2 or more targets have the same name

    ActionMacro[] macros = getAllMacros();
    for (int i = 0; i < macros.length; i++) {
      final ActionMacro macro = macros[i];
      String actionId = macro.getActionId();

      if (!registeredIds.contains(actionId)) {
        registeredIds.add(actionId);
        myActionManager.registerAction(actionId, new InvokeMacroAction(macro));
      }
    }
  }

  public void unregisterActions() {

    // unregister Tool actions
    String[] oldIds = myActionManager.getActionIds(ActionMacro.MACRO_ACTION_PREFIX);
    for (int i = 0; i < oldIds.length; i++) {
      String oldId = oldIds[i];
      myActionManager.unregisterAction(oldId);
    }
  }

  public boolean checkCanCreateMacro(String name) {
    final ActionManagerEx actionManager = (ActionManagerEx)ActionManager.getInstance();
    final String actionId = ActionMacro.MACRO_ACTION_PREFIX + name;
    if (actionManager.getAction(actionId) != null) {
      if (Messages.showYesNoDialog(IdeBundle.message("message.macro.exists", name),
                                   IdeBundle.message("title.macro.name.already.used"),
                                   Messages.getWarningIcon()) != 0) {
        return false;
      }
      actionManager.unregisterAction(actionId);
      removeMacro(name);
    }

    return true;
  }

  private void removeMacro(String name) {
    for (int i = 0; i < myMacros.size(); i++) {
      ActionMacro macro = myMacros.get(i);
      if (name.equals(macro.getName())) {
        myMacros.remove(i);
        break;
      }
    }
  }

  public boolean isPlaying() {
    return myIsPlaying;
  }

  private static class InvokeMacroAction extends AnAction {
    private final ActionMacro myMacro;

    InvokeMacroAction(ActionMacro macro) {
      myMacro = macro;
      getTemplatePresentation().setText(macro.getName(), false);
    }

    public void actionPerformed(AnActionEvent e) {
      ActionMacroManager.getInstance().playMacro(myMacro);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!ActionMacroManager.getInstance().isPlaying() &&
                                     e.getDataContext().getData(DataConstants.EDITOR) != null);
    }
  }
}
