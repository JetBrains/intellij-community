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

package com.intellij.ui.debugger.extensions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class PlaybackDebugger implements UiDebuggerExtension, PlaybackRunner.StatusCallback {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.PlaybackDebugger");


  private JPanel myComponent;

  private PlaybackRunner myRunner;

  private final DefaultListModel myMessage = new DefaultListModel();

  private final JTextField myScriptsPath = new JTextField();

  private static final String EXT = "ijs";

  private static final String DOT_EXT = "." + EXT;

  private final JTextField myCurrentScript = new JTextField();

  private VirtualFileAdapter myVfsListener;

  private boolean myChanged;
  private JList myList;

  private Document myDocument;
  private Editor myEditor;

  private PlaybackDebuggerState myState;
  private static final FileChooserDescriptor FILE_DESCRIPTOR = new ScriptFileChooserDescriptor();

  private void initUi() {
    myComponent = new JPanel(new BorderLayout());

    myState = ServiceManager.getService(PlaybackDebuggerState.class);

    final DefaultActionGroup controlGroup = new DefaultActionGroup();
    controlGroup.add(new RunOnFameActivationAction());
    controlGroup.add(new ActivateFrameAndRun());
    controlGroup.add(new StopAction());

    JPanel north = new JPanel(new BorderLayout());
    north.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, controlGroup, true).getComponent(), BorderLayout.WEST);

    final JPanel right = new JPanel(new BorderLayout());
    right.add(myCurrentScript, BorderLayout.CENTER);
    myCurrentScript.setText(myState.currentScript);
    myCurrentScript.setEditable(false);

    final DefaultActionGroup fsGroup = new DefaultActionGroup();
    SaveAction saveAction = new SaveAction();
    saveAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("control S")), myComponent);
    fsGroup.add(saveAction);
    SetScriptFileAction setScriptFileAction = new SetScriptFileAction();
    setScriptFileAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("control O")), myComponent);
    fsGroup.add(setScriptFileAction);
    AnAction newScriptAction = new NewScriptAction();
    newScriptAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke("control N")), myComponent);
    fsGroup.add(newScriptAction);

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, fsGroup, true);
    tb.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    right.add(tb.getComponent(), BorderLayout.EAST);
    north.add(right, BorderLayout.CENTER);

    myComponent.add(north, BorderLayout.NORTH);

    myDocument = EditorFactory.getInstance().createDocument("");
    myEditor = EditorFactory.getInstance().createEditor(myDocument);
    myDocument.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        myChanged = true;
      }
    });
    if (pathToFile() != null) {
      loadFrom(pathToFile());
    }

    //final String text = System.getProperty("idea.playback.script");
    //if (text != null) {
    //  ApplicationManager.getApplication().runWriteAction(new Runnable() {
    //    public void run() {
    //      myDocument.setText(text);
    //    }
    //  });
    //}

    final Splitter script2Log = new Splitter(true);
    script2Log.setFirstComponent(new JScrollPane(myEditor.getComponent()));

    myList = new JBList(myMessage);
    myList.setCellRenderer(new MyListRenderer());
    script2Log.setSecondComponent(new JScrollPane(myList));

    myComponent.add(script2Log, BorderLayout.CENTER);

    myVfsListener = new VirtualFileAdapter() {
      @Override
      public void contentsChanged(VirtualFileEvent event) {
        final VirtualFile file = pathToFile();
        if (file != null && file.equals(event.getFile())) {
          loadFrom(event.getFile());
        }
      }
    };
    LocalFileSystem.getInstance().addVirtualFileListener(myVfsListener);
  }

  private class SaveAction extends AnAction {
    private SaveAction() {
      super("Save", "", IconLoader.getIcon("/actions/menu-saveall.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myChanged);
    }

    public void actionPerformed(AnActionEvent e) {
      if (pathToFile() == null) {
        VirtualFile[] files = FileChooser.chooseFiles(myComponent, FILE_DESCRIPTOR);
        if (files.length > 0) {
          VirtualFile selectedFile = files[0];
          myState.currentScript = selectedFile.getPresentableUrl();
          myCurrentScript.setText(myState.currentScript);
        } else {
          Messages.showErrorDialog("File to save is not selected.", "Cannot save script");
          return;
        }
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          save();
        }
      });
    }
  }

  private static class ScriptFileChooserDescriptor extends FileChooserDescriptor {
    public ScriptFileChooserDescriptor() {
      super(true, false, false, false, false, false);
      putUserData(FileChooserKeys.NEW_FILE_TYPE, UiScriptFileType.getInstance());
      putUserData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT, "");
    }

    @Override
    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      if (!showHiddenFiles && FileElement.isFileHidden(file)) return false;
      return file.getExtension() != null && file.getExtension().equalsIgnoreCase(UiScriptFileType.myExtension)
             || super.isFileVisible(file, showHiddenFiles) && file.isDirectory();
    }
  }

  private class SetScriptFileAction extends AnAction {

    private SetScriptFileAction() {
      super("Set Script File", "", IconLoader.getIcon("/nodes/packageOpen.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      VirtualFile[] files = FileChooser.chooseFiles(myComponent, FILE_DESCRIPTOR, pathToFile());
      if (files.length > 0) {
        VirtualFile selectedFile = files[0];
        myState.currentScript = selectedFile.getPresentableUrl();
        loadFrom(selectedFile);
        myCurrentScript.setText(myState.currentScript);
      }
    }
  }

  private class NewScriptAction extends AnAction {
    private NewScriptAction() {
      super("New Script", "", IconLoader.getIcon("/actions/new.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myState.currentScript = "";
      myCurrentScript.setText(myState.currentScript);
      fillDocument("");
    }
  }

  private void fillDocument(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myDocument.setText(text == null ? "" : text);
      }
    });
  }

  @Nullable
  private VirtualFile pathToFile() {
    if (myState.currentScript.length() == 0) {
      return null;
    }
    return LocalFileSystem.getInstance().findFileByPath(myState.currentScript);
  }

  private void save() {
    BufferedWriter writer = null;
    try {
      final OutputStream os = pathToFile().getOutputStream(this);
      writer = new BufferedWriter(new OutputStreamWriter(os));
      final String toWrite = myDocument.getText();
      writer.write(toWrite != null ? toWrite : "");
      myChanged = false;
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), "Cannot save script");
    } finally {
      if (writer != null) {
        try {
          writer.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void loadFrom(@NotNull VirtualFile file) {
    try {
      final String text = CharsetToolkit.bytesToString(file.contentsToByteArray());
      fillDocument(text);
      myChanged = false;
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), "Cannot load file");
    }
  }

  private File getScriptsFile() {
    final String text = myScriptsPath.getText();
    if (text == null) return null;

    final File file = new File(text);
    return file.exists() ? file : null;
  }

  private class StopAction extends AnAction {
    private StopAction() {
      super("Stop", null, IconLoader.getIcon("/actions/suspend.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myRunner != null);
    }

    public void actionPerformed(AnActionEvent e) {
      if (myRunner != null) {
        myRunner.stop();
        myRunner = null;
      }
    }
  }

  private class ActivateFrameAndRun extends AnAction {
    private ActivateFrameAndRun() {
      super("Activate Frame And Run", "", IconLoader.getIcon("/nodes/deploy.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      activateAndRun();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myRunner == null);
    }
  }

  private class RunOnFameActivationAction extends AnAction {

    private RunOnFameActivationAction() {
      super("Run On Frame Activation", "", IconLoader.getIcon("/general/run.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myRunner == null);
    }

    public void actionPerformed(AnActionEvent e) {
      runOnFrame();
    }
  }

  private void activateAndRun() {
    assert myRunner == null;

    myMessage.clear();

    final IdeFrameImpl frame = getFrame();

    final Component c = ((WindowManagerEx)WindowManager.getInstance()).getFocusedComponent(frame);

    if (c != null) {
      c.requestFocus();
    } else {
      frame.requestFocus();
    }

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        startWhenFrameActive();
      }
    });

  }

  private IdeFrameImpl getFrame() {
    final Frame[] all = Frame.getFrames();
    for (Frame each : all) {
      if (each instanceof IdeFrame) {
        return (IdeFrameImpl)each;
      }
    }

    throw new IllegalStateException("Cannot find IdeFrame to run on");
  }

  private void runOnFrame() {
    assert myRunner == null;

    startWhenFrameActive();
  }

  private void startWhenFrameActive() {
    myMessage.clear();

    addInfo("Waiting for IDE frame activation", -1);
    myRunner = new PlaybackRunner(myDocument.getText(), this, false);


    new Thread() {
      @Override
      public void run() {
        new WaitFor() {
          protected boolean condition() {
            return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof IdeFrame || myRunner == null;
          }
        };                                            

        if (myRunner == null) {
          message("Script stopped", -1);
          return;
        }

        message("Starting script...", -1);

        try {
          sleep(1000);
        }
        catch (InterruptedException e) {}


        if (myRunner == null) {
          message("Script stopped", -1);
          return;
        }

        myRunner.run().doWhenProcessed(new Runnable() {
          public void run() {
            myRunner = null;
          }
        });
      }
    }.start();
  }

  public void error(final String text, final int currentLine) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        addError(text, currentLine);
      }
    });
  }

  public void message(final String text, final int currentLine) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        addInfo(text, currentLine);
      }
    });
  }

  public JComponent getComponent() {
    if (myComponent == null) {
      initUi();
    }

    return myComponent;
  }

  public String getName() {
    return "Playback";
  }

  public void dispose() {
    disposeUiResources();
  }

  @State(
      name = "PlaybackDebugger",
      storages = {
          @Storage(
              id = "other",
              file="$APP_CONFIG$/other.xml")}
  )
  public static class PlaybackDebuggerState implements PersistentStateComponent<Element> {
    private static final String ATTR_CURRENT_SCRIPT = "currentScript";
    public String currentScript = "";

    public Element getState() {
      final Element element = new Element("playback");
      element.setAttribute(ATTR_CURRENT_SCRIPT, currentScript);
      return element;
    }

    public void loadState(Element state) {
      final String path = state.getAttributeValue(ATTR_CURRENT_SCRIPT);
      if (path != null) {
        currentScript = path;
      }
    }
  }

  public void disposeUiResources() {
    myComponent = null;
    LocalFileSystem.getInstance().removeVirtualFileListener(myVfsListener);
    //System.setProperty("idea.playback.script", myDocument.getText());
    myCurrentScript.setText("");
    myMessage.clear();
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  private void addInfo(String text, int line) {
    myMessage.addElement(new ListElement(text, false, line));
    scrollToLast();
  }

  private void addError(String text, int line) {
    myMessage.addElement(new ListElement(text, true, line));
    scrollToLast();
  }

  private void scrollToLast() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myMessage.size() == 0) return;

        final Rectangle rec = myList.getCellBounds(myMessage.getSize() - 1, myMessage.getSize() - 1);
        myList.scrollRectToVisible(rec);
      }
    });
  }

  private class ListElement {
    private final String myText;
    private final boolean myError;

    private final int myLine;

    public ListElement(String text, boolean isError, int line) {
      myText = text;
      myError = isError;
      myLine = line;
    }

    public String getText() {
      return myText;
    }

    public boolean isError() {
      return myError;
    }

    public int getLine() {
      return myLine;
    }
  }

  private class MyListRenderer extends ColoredListCellRenderer  {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      final ListElement listElement = (ListElement)value;
      final String text = (listElement.getLine() >= 0 ? "Line " + listElement.getLine() + ":" : "") + listElement.getText();
      append(text, listElement.isError() ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
