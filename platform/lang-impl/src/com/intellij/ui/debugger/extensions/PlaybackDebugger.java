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
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.impl.FileTreeStructure;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.ui.popup.util.BaseTreePopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.*;

@State(
    name = "PlaybackDebugger",
    storages = {
        @Storage(
            id = "other",
            file="$APP_CONFIG$/other.xml")}
)
public class PlaybackDebugger implements UiDebuggerExtension, PlaybackRunner.StatusCallback, PersistentStateComponent<Element> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.PlaybackDebugger");


  private JPanel myComponent;

  private PlaybackRunner myRunner;

  private DefaultListModel myMessage = new DefaultListModel();

  private JTextField myScriptsPath = new JTextField();

  private static final String EXT = "ijs";

  private static final String DOT_EXT = "." + EXT;

  private JTextField myCurrentScript = new JTextField();

  private VirtualFileAdapter myVfsListener;

  private boolean myChanged;
  private JList myList;

  private Document myDocument;
  private Editor myEditor;

  private void initUi() {
    myComponent = new JPanel(new BorderLayout());

    final DocumentListener docListener = new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        myChanged = true;
      }

      public void removeUpdate(DocumentEvent e) {
        myChanged = true;
      }

      public void changedUpdate(DocumentEvent e) {
        myChanged = true;
      }
    };


    final DefaultActionGroup controlGroup = new DefaultActionGroup();
    controlGroup.add(new RunOnFameActivationAction());
    controlGroup.add(new ActivateFrameAndRun());
    controlGroup.addSeparator();
    controlGroup.add(new StopAction());
    controlGroup.addSeparator();
    controlGroup.add(new SaveAction());

    JPanel north = new JPanel(new BorderLayout());
    north.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, controlGroup, true).getComponent(), BorderLayout.WEST);

    final JPanel right = new JPanel(new BorderLayout());
    myCurrentScript.getDocument().addDocumentListener(docListener);
    right.add(myCurrentScript, BorderLayout.CENTER);


    final DefaultActionGroup loadGroup = new DefaultActionGroup();
    loadGroup.add(new LoadFromFileAction());
    loadGroup.addSeparator();
    loadGroup.add(new SetScriptDirAction());

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, loadGroup, true);
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

    final String text = System.getProperty("idea.playback.script");
    if (text != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          myDocument.setText(text);
        }
      });
    }

    final Splitter script2Log = new Splitter(true);
    script2Log.setFirstComponent(new JScrollPane(myEditor.getComponent()));

    myList = new JList(myMessage);
    myList.setCellRenderer(new MyListRenderer());
    script2Log.setSecondComponent(new JScrollPane(myList));

    myComponent.add(script2Log, BorderLayout.CENTER);

    myVfsListener = new VirtualFileAdapter() {
      @Override
      public void contentsChanged(VirtualFileEvent event) {
        final VirtualFile file = getCurrentScriptFile();
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
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          save();
        }
      });
    }
  }

  private VirtualFile getCurrentScriptFile() {
    final String text = myCurrentScript.getText();
    return text != null ? LocalFileSystem.getInstance().findFileByIoFile(new File(text)) : null;
  }

  private class SetScriptDirAction extends AnAction {
    private SetScriptDirAction() {
      super("Set Script Directory", "", IconLoader.getIcon("/nodes/packageOpen.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final File file = getScriptsFile();
      final String choose = file != null ? "Choose another..." : "Choose...";
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>("Script Directory", new String[] {
        file != null ? file.getAbsolutePath() : "Undefined", choose
      }) {
        @Override
        public PopupStep onChosen(String selectedValue, boolean finalChoice) {
          if (selectedValue != choose) return FINAL_CHOICE;

          final VirtualFile[] files =
            FileChooser.chooseFiles(myComponent, new FileChooserDescriptor(false, true, false, false, false, false));
            if (files.length > 0) {
              myScriptsPath.setText(files[0].getPresentableUrl());
            }
          return FINAL_CHOICE;
        }
      }).showUnderneathOf(e.getInputEvent().getComponent());
    }
  }

  private class LoadFromFileAction extends AnAction {
    private LoadFromFileAction() {
      super("Load", "Load script from the script directory", IconLoader.getIcon("/general/autoscrollFromSource.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final Component c = e.getInputEvent().getComponent();
      final File scriptsFile = getScriptsFile();

      final FilenameFilter filter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.toLowerCase().endsWith(DOT_EXT) || new File(dir, name).isDirectory();
        }
      };

      final File[] kids = scriptsFile != null ? scriptsFile.listFiles(filter) : null;

      if (kids == null || kids.length == 0) {
        JBPopupFactory.getInstance().createMessage("No scripts found in the given directory").showUnderneathOf(c);
      } else {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, false) {
          @Override
          public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
            final boolean fileVisible = super.isFileVisible(file, showHiddenFiles);
            if (fileVisible && file.getParent() != null) {
              return filter.accept(new File(file.getParent().getPresentableUrl()), file.getName());
            } else {
              return false;
            }
          }
        };
        descriptor.setRoot(LocalFileSystem.getInstance().findFileByIoFile(scriptsFile));
        JBPopupFactory.getInstance().createTree(new BaseTreePopupStep<FileElement>(null, "Choose Script To Load", new FileTreeStructure(null, descriptor)) {
          @Override
          public PopupStep onChosen(FileElement selectedValue, boolean finalChoice) {
            loadFrom(selectedValue.getFile());
            return FINAL_CHOICE;
          }
        }).showUnderneathOf(c);
      }
    }
  }

  private boolean maybeCreateFile()  {
    if (getCurrentScriptFile() != null) return true;

    try {
      final String text = myCurrentScript.getText();
      if (text == null) {
        throw new Exception("Cannot create file with name:" + text);
      }

      final File file = new File(text);
      final File parentFile = file.getParentFile();

      try {
        final VirtualFile parent = VfsUtil.createDirectories(parentFile.getAbsolutePath());

        parent.createChildData(this, file.getName());
      }
      catch (IOException e) {
        throw new Exception(e.getMessage());
      }

      return true;
    }
    catch (Exception e) {
      Messages.showErrorDialog(e.getMessage(), "Cannot Save File");
      return false;
    }
  }

  private void save() {
    BufferedWriter writer = null;
    try {
      if (!maybeCreateFile()) return;

      final OutputStream os = getCurrentScriptFile().getOutputStream(this);
      writer = new BufferedWriter(new OutputStreamWriter(os));
      final String toWrite = myDocument.getText();
      writer.write(toWrite != null ? toWrite : "");
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

  private void loadFrom(VirtualFile file) {
    try {
      final String text = CharsetToolkit.bytesToString(file.contentsToByteArray());
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          myDocument.setText(text);
        }
      });
      myCurrentScript.setText(file.getPresentableUrl());
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

  public Element getState() {
    final Element element = new Element("playback");

    final String text = myScriptsPath.getText();

    if (text != null) {
      element.setAttribute("scriptDir", text);
    }

    return element;
  }

  public void loadState(Element state) {
    final String dir = state.getAttributeValue("scriptDir");
    if (dir != null) {
      myScriptsPath.setText(dir);
    }
  }

  public void disposeUiResources() {
    myComponent = null;
    LocalFileSystem.getInstance().removeVirtualFileListener(myVfsListener);
    System.setProperty("idea.playback.script", myDocument.getText());
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
    private String myText;
    private boolean myError;

    private int myLine;

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