/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actionMacro;

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
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
  private final ActionManagerEx myActionManager;
  private ActionMacro myLastMacro;
  private ActionMacro myRecordingMacro;
  private ArrayList<ActionMacro> myMacros = new ArrayList<ActionMacro>();
  private String myLastMacroName = null;
  private boolean myIsPlaying = false;
  @NonNls
  private static final String ELEMENT_MACRO = "macro";
  private final IdeEventQueue.EventDispatcher myKeyProcessor;

  private InputEvent myLastActionInputEvent;

  public ActionMacroManager(ActionManagerEx actionManagerEx) {
    myActionManager = actionManagerEx;
    myActionManager.addAnActionListener(new AnActionListener() {
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (myIsRecording) {
          String id = myActionManager.getId(action);
          //noinspection HardCodedStringLiteral
          if (id != null && !"StartStopMacroRecording".equals(id)) {
            myRecordingMacro.appendAction(id);
          }

          myLastActionInputEvent = event.getInputEvent();
        }
      }

      public void beforeEditorTyping(char c, DataContext dataContext) {
      }

      public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
      }
    });

    myKeyProcessor = new MyKeyPostpocessor();
    IdeEventQueue.getInstance().addPostprocessor(myKeyProcessor, null);
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
    for (ActionMacro macro : myMacros) {
      Element macroElement = new Element(ELEMENT_MACRO);
      macro.writeExternal(macroElement);
      element.addContent(macroElement);
    }
  }

  public static ActionMacroManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ActionMacroManager.class);
  }

  @NotNull
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
    final IdeFrame frame = WindowManager.getInstance().getIdeFrame(null);
    assert frame != null;

    StringBuffer script = new StringBuffer();
    ActionMacro.ActionDescriptor[] actions = macro.getActions();
    for (ActionMacro.ActionDescriptor each : actions) {
      each.generateTo(script);
    }

    final PlaybackRunner runner = new PlaybackRunner(script.toString(), new PlaybackRunner.StatusCallback.Edt() {
      public void errorEdt(String text, int curentLine) {
        frame.getStatusBar().setInfo("Line " + curentLine + ":" + " Error: " + text);
      }

      public void messageEdt(String text, int curentLine) {
        frame.getStatusBar().setInfo("Line " + curentLine + ": " + text);
      }
    }, Registry.is("actionSystem.playback.useDirectActionCall"));

    myIsPlaying = true;

    runner.run().doWhenDone(new Runnable() {
      public void run() {
        frame.getStatusBar().setInfo("Script execution finished");
      }
    }).doWhenProcessed(new Runnable() {
      public void run() {
        myIsPlaying = false;
      }
    });
  }

  public boolean isRecording() {
    return myIsRecording;
  }

  public void disposeComponent() {
    IdeEventQueue.getInstance().removePostprocessor(myKeyProcessor);
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
    for (final ActionMacro macro : macros) {
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
    for (final String oldId : oldIds) {
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
      getInstance().playMacro(myMacro);
    }

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!getInstance().isPlaying() &&
                                     PlatformDataKeys.EDITOR.getData(e.getDataContext()) != null);
    }
  }

  private class MyKeyPostpocessor implements IdeEventQueue.EventDispatcher {

    public boolean dispatch(AWTEvent e) {
      if (isRecording() && e instanceof KeyEvent) {
        postProcessKeyEvent((KeyEvent)e);
      }
      return false;
    }

    public void postProcessKeyEvent(KeyEvent e) {
      final boolean isChar = e.getKeyChar() != KeyEvent.CHAR_UNDEFINED;
      boolean hasActionModifiers = e.isAltDown() | e.isControlDown() | e.isMetaDown();
      boolean plainType = isChar && !hasActionModifiers;
      final boolean isEnter = e.getKeyCode() == KeyEvent.VK_ENTER;

      boolean noModifierKeyIsPressed =  e.getKeyCode() != KeyEvent.VK_CONTROL
                 && e.getKeyCode() != KeyEvent.VK_ALT
                 && e.getKeyCode() != KeyEvent.VK_META
                 && e.getKeyCode() != KeyEvent.VK_SHIFT;

      if (e.getID() == KeyEvent.KEY_PRESSED && plainType && !isEnter) {
         myRecordingMacro.appendKeytyped(e.getKeyChar(), e.getKeyCode(), e.getModifiers());
      } else if (e.getID() == KeyEvent.KEY_PRESSED && noModifierKeyIsPressed && (!plainType || isEnter)) {
        final boolean waiting = IdeEventQueue.getInstance().getKeyEventDispatcher().isWaitingForSecondKeyStroke();
        if ((!e.equals(myLastActionInputEvent) && !waiting) || isEnter) {
          final String stroke = KeyStroke.getKeyStrokeForEvent(e).toString();

          final int pressed = stroke.indexOf("pressed");
          String key = stroke.substring(pressed + "pressed".length());
          String modifiers = stroke.substring(0, pressed);

          String ready = (modifiers.replaceAll("ctrl", "control").trim() + " " + key.trim()).trim();
          
          myRecordingMacro.appendShortuct(ready);
          if (!isEnter) {
            myLastActionInputEvent = null;
          }
        }
      }
    }
  }
}
