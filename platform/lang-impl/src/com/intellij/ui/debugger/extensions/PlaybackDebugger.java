// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.debugger.extensions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

@SuppressWarnings("DialogTitleCapitalization")
public final class PlaybackDebugger implements UiDebuggerExtension, PlaybackRunner.StatusCallback {
  private static final Logger LOG = Logger.getInstance(PlaybackDebugger.class);

  private static final Color ERROR_COLOR = JBColor.RED;
  @SuppressWarnings("UseJBColor")
  private static final Color MESSAGE_COLOR = Color.BLACK;
  private static final Color CODE_COLOR = PlatformColors.BLUE;
  private static final Color TEST_COLOR = JBColor.GREEN.darker();

  private JPanel myComponent;

  private PlaybackRunner runner;

  private JEditorPane myLog;

  private final JTextField myCurrentScript = new JTextField();

  private VirtualFileListener myVfsListener;

  private boolean myChanged;

  private PlaybackDebuggerState myState;
  private static final FileChooserDescriptor FILE_DESCRIPTOR = new ScriptFileChooserDescriptor();
  private JTextArea myCodeEditor;

  private void initUi() {
    myComponent = new JPanel(new BorderLayout());
    myLog = new JEditorPane();
    myLog.setEditorKit(new StyledEditorKit());
    myLog.setEditable(false);


    myState = ApplicationManager.getApplication().getService(PlaybackDebuggerState.class);

    final DefaultActionGroup controlGroup = new DefaultActionGroup();
    controlGroup.add(new RunOnFameActivationAction());
    controlGroup.add(new ActivateFrameAndRun());
    controlGroup.add(new StopAction());

    JPanel north = new JPanel(new BorderLayout());
    north.add(ActionManager.getInstance().createActionToolbar("PlaybackDbg", controlGroup, true).getComponent(), BorderLayout.WEST);

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

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("PlaybackDbgExtra", fsGroup, true);
    tb.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    right.add(tb.getComponent(), BorderLayout.EAST);
    north.add(right, BorderLayout.CENTER);

    myComponent.add(north, BorderLayout.NORTH);

    myCodeEditor = new JTextArea();
    myCodeEditor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        myChanged = true;
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        myChanged = true;
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        myChanged = true;
      }
    });
    if (pathToFile() != null) {
      loadFrom(Objects.requireNonNull(pathToFile()));
    }

    final Splitter script2Log = new Splitter(true);
    script2Log.setFirstComponent(ScrollPaneFactory.createScrollPane(myCodeEditor));

    script2Log.setSecondComponent(ScrollPaneFactory.createScrollPane(myLog));

    myComponent.add(script2Log, BorderLayout.CENTER);

    myVfsListener = new VirtualFileListener() {
      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        final VirtualFile file = pathToFile();
        if (file != null && file.equals(event.getFile())) {
          loadFrom(event.getFile());
        }
      }
    };
    LocalFileSystem.getInstance().addVirtualFileListener(myVfsListener);
  }

  private final class SaveAction extends AnAction {
  SaveAction() {
    super(IdeBundle.messagePointer("action.AnAction.text.save"),
          IdeBundle.messagePointer("action.AnAction.description.save"), AllIcons.Actions.MenuSaveall);
  }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myChanged);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (pathToFile() == null) {
        VirtualFile selectedFile = FileChooser.chooseFile(FILE_DESCRIPTOR, myComponent, getEventProject(e), null);
        if (selectedFile != null) {
          myState.currentScript = selectedFile.getPresentableUrl();
          myCurrentScript.setText(myState.currentScript);
        }
        else {
          //noinspection DialogTitleCapitalization
          Messages.showErrorDialog(LangBundle.message("dialog.message.file.to.save.selected"),
                                   LangBundle.message("dialog.title.cannot.save.script"));
          return;
        }
      }
      ApplicationManager.getApplication().runWriteAction(() -> save());
    }
  }

  private static final class ScriptFileChooserDescriptor extends FileChooserDescriptor {
    ScriptFileChooserDescriptor() {
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

  private final class SetScriptFileAction extends AnAction {
  SetScriptFileAction() {
    //noinspection DialogTitleCapitalization
    super(IdeBundle.messagePointer("action.AnAction.text.set.script.file"),
          IdeBundle.messagePointer("action.AnAction.description.set.script.file"), AllIcons.Actions.MenuOpen);
  }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      VirtualFile selectedFile = FileChooser.chooseFile(FILE_DESCRIPTOR, myComponent, getEventProject(e), pathToFile());
      if (selectedFile != null) {
        myState.currentScript = selectedFile.getPresentableUrl();
        loadFrom(selectedFile);
        myCurrentScript.setText(myState.currentScript);
      }
    }
  }

  @SuppressWarnings("DialogTitleCapitalization")
  private final class NewScriptAction extends AnAction {
  NewScriptAction() {
    super(IdeBundle.messagePointer("action.AnAction.text.new.script"),
          IdeBundle.messagePointer("action.AnAction.description.new.script"), AllIcons.Actions.New);
  }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myState.currentScript = "";
      myCurrentScript.setText(myState.currentScript);
      fillDocument("");
    }
  }

  private void fillDocument(final String text) {
    ApplicationManager.getApplication().runWriteAction(() -> myCodeEditor.setText(text == null ? "" : text));
  }

  private @Nullable VirtualFile pathToFile() {
    if (myState.currentScript.isEmpty()) {
      return null;
    }
    return LocalFileSystem.getInstance().findFileByPath(myState.currentScript);
  }

  private void save() {
    try {
      VirtualFile file = pathToFile();
      final String toWrite = myCodeEditor.getText();
      String text = toWrite != null ? toWrite : "";
      assert file != null;
      VfsUtil.saveText(file, text);
      myChanged = false;
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), LangBundle.message("dialog.title.cannot.save.script"));
    }
  }

  private void loadFrom(@NotNull VirtualFile file) {
    final String text = LoadTextUtil.loadText(file).toString();
    fillDocument(text);
    myChanged = false;
  }

  private final class StopAction extends AnAction {
  StopAction() {
    super(IdeBundle.messagePointer("action.AnAction.text.stop"),
          IdeBundle.messagePointer("action.AnAction.description.stop"), AllIcons.Actions.Suspend);
  }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(runner != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (runner != null) {
        runner.stop();
        SwingUtilities.invokeLater(() -> runner = null);
      }
    }
  }

  private final class ActivateFrameAndRun extends AnAction {
  ActivateFrameAndRun() {
    super(IdeBundle.messagePointer("action.AnAction.text.activate.frame.and.run"),
          IdeBundle.messagePointer("action.AnAction.description.activate.frame.and.run"), AllIcons.Nodes.Deploy);
  }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      activateAndRun();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(runner == null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private final class RunOnFameActivationAction extends AnAction {
  RunOnFameActivationAction() {
    super(IdeBundle.messagePointer("action.AnAction.text.run.on.frame.activation"),
          IdeBundle.messagePointer("action.AnAction.description.run.on.frame.activation"), AllIcons.RunConfigurations.TestState.Run);
  }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(runner == null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      runOnFrame();
    }
  }

  private void activateAndRun() {
    assert runner == null;

    myLog.setText(null);

    JFrame frame = getFrame();
    Component c = WindowManager.getInstance().getFocusedComponent(frame);
    if (c == null) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(frame, true));
    }
    else {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(c, true));
    }

    SwingUtilities.invokeLater(() -> startWhenFrameActive());
  }

  private static JFrame getFrame() {
    final Frame[] all = Frame.getFrames();
    for (Frame each : all) {
      if (each instanceof IdeFrameImpl) {
        return (JFrame)each;
      }
    }

    throw new IllegalStateException("Cannot find IdeFrame to run on");
  }

  private void runOnFrame() {
    assert runner == null;

    startWhenFrameActive();
  }

  private void startWhenFrameActive() {
    myLog.setText(null);

    addInfo("Waiting for IDE frame activation", -1, MESSAGE_COLOR, 0);
    runner = new PlaybackRunner(myCodeEditor.getText(), this, false, true, false);
    VirtualFile file = pathToFile();
    if (file != null) {
      VirtualFile scriptDir = file.getParent();
      if (scriptDir != null) {
        runner.setScriptDir(new File(scriptDir.getPresentableUrl()));
      }
    }

    new Thread("playback debugger") {
      @Override
      public void run() {
        new WaitFor(60000) {
          @Override
          protected boolean condition() {
            return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof IdeFrame || runner == null;
          }
        };

        if (runner == null) {
          message(null, "Script stopped", -1, Type.message, true);
          return;
        }

        message(null, "Starting script...", -1, Type.message, true);

        TimeoutUtil.sleep(1000);


        if (runner == null) {
          message(null, "Script stopped", -1, Type.message, true);
          return;
        }

        PlaybackRunner runner = PlaybackDebugger.this.runner;
        PlaybackDebugger.this.runner.run().whenComplete((o, throwable) -> {
          if (runner == PlaybackDebugger.this.runner) {
            SwingUtilities.invokeLater(() -> PlaybackDebugger.this.runner = null);
          }
        });
      }
    }.start();
  }

  @Override
  public void message(final @Nullable PlaybackContext context, final String text, final Type type) {
    message(context, text, context != null ? context.getCurrentLine() : -1, type, false);
  }

  private void message(final @Nullable PlaybackContext context, final String text, final int currentLine, final Type type, final boolean forced) {
    final int depth = context != null ? context.getCurrentStageDepth() : 0;

    UIUtil.invokeLaterIfNeeded(() -> {
      if (!forced && (context != null && context.isDisposed())) return;

      Color color = switch (type) {
        case message -> MESSAGE_COLOR;
        case error -> ERROR_COLOR;
        case code -> CODE_COLOR;
        case test -> TEST_COLOR;
      };
      addInfo(text, currentLine, color, depth);
    });
  }

  @Override
  public JComponent getComponent() {
    if (myComponent == null) {
      initUi();
    }

    return myComponent;
  }

  @Override
  public @NlsContexts.TabTitle String getName() {
    return LangBundle.message("tab.title.playback");
  }

  public void dispose() {
    disposeUiResources();
  }

  @State(
    name = "PlaybackDebugger",
    storages = @Storage(value = "playbackDebugger.xml", roamingType = RoamingType.PER_OS),
    category = SettingsCategory.TOOLS
  )
  public static final class PlaybackDebuggerState implements PersistentStateComponent<PlaybackDebuggerState> {
    @Attribute
    public String currentScript = "";

    @Override
    public PlaybackDebuggerState getState() {
      return this;
    }

    @Override
    public void loadState(@NotNull PlaybackDebuggerState state) {
      XmlSerializerUtil.copyBean(state, this);
    }
  }

  @Override
  public void disposeUiResources() {
    myComponent = null;
    LocalFileSystem.getInstance().removeVirtualFileListener(myVfsListener);
    myCurrentScript.setText("");
    myLog.setText(null);
  }

  private void addInfo(String text, int line, Color fg, int depth) {
    if (text == null || text.isEmpty()) return;

    String inset = "   ".repeat(depth);
    Document doc = myLog.getDocument();
    SimpleAttributeSet attr = new SimpleAttributeSet();
    StyleConstants.setFontFamily(attr, UIManager.getFont("Label.font").getFontName());
    StyleConstants.setFontSize(attr, UIManager.getFont("Label.font").getSize());
    StyleConstants.setForeground(attr, fg);
    try {
      doc.insertString(doc.getLength(), inset + text + "\n", attr);
    }
    catch (BadLocationException e) {
      LOG.error(e);
    }
    scrollToLast();
  }

  private void scrollToLast() {
    SwingUtilities.invokeLater(() -> {
      if (myLog.getDocument().getLength() == 0) return;

      Rectangle bounds = myLog.getBounds();
      myLog.scrollRectToVisible(new Rectangle(0, (int)bounds.getMaxY() - 1, (int)bounds.getWidth(), 1));
    });
  }

}
