// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionMacro;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.customization.ActionUrl;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AnimatedIcon.Recording;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.*;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

@State(name = "ActionMacroManager", storages = @Storage("macros.xml"), category = SettingsCategory.UI)
public final class ActionMacroManager implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(ActionMacroManager.class);

  private static final String TYPING_SAMPLE = "WWWWWWWWWWWWWWWWWWWW";
  public static final String NO_NAME_NAME = "<noname>";

  private boolean myIsRecording;
  private ActionMacro myLastMacro;
  private ActionMacro myRecordingMacro;
  private ArrayList<ActionMacro> myMacros = new ArrayList<>();
  private String myLastMacroName = null;
  private boolean myIsPlaying = false;
  @NonNls
  private static final String ELEMENT_MACRO = "macro";
  private final IdeEventQueue.EventDispatcher myKeyProcessor;

  private final Set<InputEvent> myLastActionInputEvent = new HashSet<>();
  private ActionMacroManager.Widget myWidget;

  private String myLastTyping = "";

  ActionMacroManager() {
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        String id = ActionManager.getInstance().getId(action);
        if (id == null) {
          return;
        }

        if ("StartStopMacroRecording".equals(id)) {
          myLastActionInputEvent.add(event.getInputEvent());
        }
        else if (myIsRecording) {
          myRecordingMacro.appendAction(id);
          String shortcut = null;
          if (event.getInputEvent() instanceof KeyEvent) {
            shortcut = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStrokeForEvent((KeyEvent)event.getInputEvent()));
          }
          notifyUser(id + (shortcut != null ? " (" + shortcut + ")" : ""), false);
          myLastActionInputEvent.add(event.getInputEvent());
        }
      }
    });

    myKeyProcessor = new KeyPostProcessor();
    IdeEventQueue.getInstance().addPostprocessor(myKeyProcessor, null);
  }

  static final class MyActionTuner implements ActionConfigurationCustomizer {
    @Override
    public void customize(@NotNull ActionManager actionManager) {
      // load state will call ActionManager, but ActionManager is not yet ready, so, postpone
      NonUrgentExecutor.getInstance().execute(() -> getInstance());
    }
  }

  @Override
  public void loadState(@NotNull Element state) {
    myMacros = new ArrayList<>();
    for (Element macroElement : state.getChildren(ELEMENT_MACRO)) {
      ActionMacro macro = new ActionMacro();
      macro.readExternal(macroElement);
      myMacros.add(macro);
    }

    registerActions(ActionManager.getInstance());
  }

  @NotNull
  @Override
  public Element getState() {
    Element element = new Element("state");
    for (ActionMacro macro : myMacros) {
      Element macroElement = new Element(ELEMENT_MACRO);
      macro.writeExternal(macroElement);
      element.addContent(macroElement);
    }
    return element;
  }

  public static ActionMacroManager getInstance() {
    return ApplicationManager.getApplication().getService(ActionMacroManager.class);
  }

  public void startRecording(Project project, String macroName) {
    LOG.assertTrue(!myIsRecording);
    myIsRecording = true;
    myRecordingMacro = new ActionMacro(macroName);

    IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
    if (frame == null) {
      LOG.warn("Cannot start macro recording: ide frame not found");
      return;
    }
    StatusBar statusBar = frame.getStatusBar();
    if (statusBar == null) {
      LOG.warn("Cannot start macro recording: status bar not found");
      return;
    }
    myWidget = new Widget(statusBar);
    statusBar.addWidget(myWidget);
  }

  private final class Widget implements CustomStatusBarWidget, Consumer<MouseEvent> {
    private final AnimatedIcon myIcon = new AnimatedIcon("Macro recording",
                                                         Recording.ICONS.toArray(new Icon[0]),
                                                         AllIcons.Ide.Macro.Recording_1,
                                                         Recording.DELAY * Recording.ICONS.size());
    private final StatusBar myStatusBar;
    private final WidgetPresentation myPresentation;

    private final JPanel myBalloonComponent;
    private Balloon myBalloon;
    private final JLabel myText;

    private Widget(StatusBar statusBar) {
      myStatusBar = statusBar;
      myIcon.setBorder(JBUI.CurrentTheme.StatusBar.Widget.iconBorder());
      myPresentation = new WidgetPresentation() {
        @Override
        public String getTooltipText() {
          return IdeBundle.message("tooltip.macro.is.being.recorded.now");
        }

        @Override
        public Consumer<MouseEvent> getClickConsumer() {
          return Widget.this;
        }
      };


      new BaseButtonBehavior(myIcon) {
        @Override
        protected void execute(MouseEvent e) {
          showBalloon();
        }
      };

      myBalloonComponent = new NonOpaquePanel(new BorderLayout());

      final AnAction stopAction = ActionManager.getInstance().getAction("StartStopMacroRecording");
      final DefaultActionGroup group = new DefaultActionGroup();
      group.add(stopAction);
      final ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.STATUS_BAR_PLACE, group, true);
      tb.setMiniMode(true);

      final NonOpaquePanel top = new NonOpaquePanel(new BorderLayout());
      top.add(tb.getComponent(), BorderLayout.WEST);
      myText = new JLabel(IdeBundle.message("status.bar.text.macro.recorded", "..." + TYPING_SAMPLE), SwingConstants.LEFT);
      final Dimension preferredSize = myText.getPreferredSize();
      myText.setPreferredSize(preferredSize);
      myText.setText(IdeBundle.message("label.macro.recording.started"));
      myLastTyping = "";
      top.add(myText, BorderLayout.CENTER);
      myBalloonComponent.add(top, BorderLayout.CENTER);
    }

    private void showBalloon() {
      if (myBalloon != null) {
        Disposer.dispose(myBalloon);
        return;
      }

      myBalloon = JBPopupFactory.getInstance().createBalloonBuilder(myBalloonComponent)
        .setAnimationCycle(200)
        .setCloseButtonEnabled(true)
        .setHideOnAction(false)
        .setHideOnClickOutside(false)
        .setHideOnFrameResize(false)
        .setHideOnKeyOutside(false)
        .setSmallVariant(true)
        .setShadow(true)
        .createBalloon();

      Disposer.register(myBalloon, new Disposable() {
        @Override
        public void dispose() {
          myBalloon = null;
        }
      });

      myBalloon.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (myBalloon != null) {
            Disposer.dispose(myBalloon);
          }
        }
      });

      myBalloon.show(new PositionTracker<>(myIcon) {
        @Override
        public RelativePoint recalculateLocation(@NotNull Balloon object) {
          return new RelativePoint(myIcon, new Point(myIcon.getSize().width / 2, 4));
        }
      }, Balloon.Position.above);
    }

    @Override
    public JComponent getComponent() {
      return myIcon;
    }

    @NotNull
    @Override
    public String ID() {
      return "MacroRecording";
    }

    @Override
    public void consume(MouseEvent mouseEvent) {
    }

    @Override
    public WidgetPresentation getPresentation() {
      return myPresentation;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      showBalloon();
    }

    @Override
    public void dispose() {
      Disposer.dispose(myIcon);
      if (myBalloon != null) {
        Disposer.dispose(myBalloon);
      }
    }

    public void delete() {
      if (myBalloon != null) {
        Disposer.dispose(myBalloon);
      }
      myStatusBar.removeWidget(ID());
    }

    public void notifyUser(@Nls String text) {
      myText.setText(text);
      myText.revalidate();
      myText.repaint();
    }
  }

  public void stopRecording(@Nullable Project project) {
    LOG.assertTrue(myIsRecording);

    if (myWidget != null) {
      myWidget.delete();
      myWidget = null;
    }

    myIsRecording = false;
    myLastActionInputEvent.clear();
    String macroName = "";
    do {
      macroName = Messages.showInputDialog(project,
                                           IdeBundle.message("prompt.enter.macro.name"),
                                           IdeBundle.message("title.enter.macro.name"),
                                           Messages.getQuestionIcon(), macroName, null);
      if (macroName == null) {
        myRecordingMacro = null;
        return;
      }

      if (macroName.isEmpty()) macroName = null;
    }
    while (macroName != null && !checkCanCreateMacro(project, macroName));

    myLastMacro = myRecordingMacro;
    addRecordedMacroWithName(macroName);
    registerActions(ActionManager.getInstance());
  }

  private void addRecordedMacroWithName(@Nullable String macroName) {
    if (macroName != null) {
      myRecordingMacro.setName(macroName);
      myMacros.add(myRecordingMacro);
      myRecordingMacro = null;
    }
    else {
      for (int i = 0; i < myMacros.size(); i++) {
        ActionMacro macro = myMacros.get(i);
        if (NO_NAME_NAME.equals(macro.getName())) {
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

      @Override
      public void messageEdt(PlaybackContext context, @NlsContexts.StatusBarText String text, Type type) {
        if (type == Type.message || type == Type.error) {
          StatusBar statusBar = frame.getStatusBar();
          if (statusBar != null) {
            if (context != null) {
              text = IdeBundle.message("status.bar.message.at.line", context.getCurrentLine(), text);
            }
            statusBar.setInfo(text);
          }
        }
      }
    }, Registry.is("actionSystem.playback.useDirectActionCall"), true, Registry.is("actionSystem.playback.useTypingTargets"));

    myIsPlaying = true;

    runner.run()
      .doWhenDone(() -> {
        StatusBar statusBar = frame.getStatusBar();
        statusBar.setInfo(IdeBundle.message("status.bar.text.script.execution.finished"));
      })
      .doWhenProcessed(() -> myIsPlaying = false);
  }

  public boolean isRecording() {
    return myIsRecording;
  }

  @Override
  public void dispose() {
    IdeEventQueue.getInstance().removePostprocessor(myKeyProcessor);
  }

  public ActionMacro[] getAllMacros() {
    return myMacros.toArray(new ActionMacro[0]);
  }

  public void removeAllMacros() {
    if (myLastMacro != null) {
      myLastMacroName = myLastMacro.getName();
      myLastMacro = null;
    }
    myMacros = new ArrayList<>();
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

  public void registerActions(@NotNull ActionManager actionManager) {
    registerActions(actionManager, Collections.emptyMap());
  }

  public void registerActions(@NotNull ActionManager actionManager, @NotNull Map<String, String> renamingMap) {
    // unregister Tool actions
    Map<String, Icon> icons = new HashMap<>();
    for (String oldId : actionManager.getActionIdList(ActionMacro.MACRO_ACTION_PREFIX)) {
      final AnAction action = actionManager.getAction(oldId);
      if (action != null) {
        final Icon icon = action.getTemplatePresentation().getIcon();
        if (icon != null) {
          final String newId = renamingMap.get(oldId);
          icons.put((newId == null) ? oldId : newId, icon);
        }
      }
      actionManager.unregisterAction(oldId);
    }

    // to prevent exception if 2 or more targets have the same name
    Set<String> registeredIds = new HashSet<>();

    for (ActionMacro macro : getAllMacros()) {
      String actionId = macro.getActionId();
      if (!registeredIds.contains(actionId)) {
        registeredIds.add(actionId);
        final InvokeMacroAction action = new InvokeMacroAction(macro);
        final Icon icon = icons.get(actionId);
        if (icon != null) {
          action.getTemplatePresentation().setIcon(icon);
        }
        actionManager.registerAction(actionId, action);
      }
    }

    // fix references to and icons of renamed macros in the custom actions schema
    final CustomActionsSchema customActionsSchema = CustomActionsSchema.getInstance();
    final List<ActionUrl> actions = customActionsSchema.getActions();
    for (final ActionUrl actionUrl : actions) {
      final String newId = renamingMap.get(actionUrl.getComponent());
      if (newId != null) {
        actionUrl.setComponent(newId);
      }
    }
    customActionsSchema.setActions(actions);
    for (Map.Entry<String, String> entry : renamingMap.entrySet()) {
      final String oldId = entry.getKey();
      final String path = customActionsSchema.getIconPath(oldId);
      if (!path.isEmpty()) {
        final String newId = entry.getValue();
        customActionsSchema.removeIconCustomization(oldId);
        customActionsSchema.addIconCustomization(newId, path);
      }
    }
    if (!renamingMap.isEmpty()) CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
  }

  private boolean checkCanCreateMacro(Project project, String name) {
    final ActionManagerEx actionManager = (ActionManagerEx)ActionManager.getInstance();
    final String actionId = ActionMacro.MACRO_ACTION_PREFIX + name;
    if (actionManager.getAction(actionId) != null) {
      if (!MessageDialogBuilder.yesNo(IdeBundle.message("title.macro.name.already.used"), IdeBundle.message("message.macro.exists", name))
            .icon(Messages.getWarningIcon()).ask(project)) {
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

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      IdeEventQueue.getInstance().doWhenReady(() -> getInstance().playMacro(myMacro));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!getInstance().isPlaying());
    }

    @ActionText
    @Nullable
    @Override
    public String getTemplateText() {
      return IdeBundle.message("action.invoke.macro.text");
    }
  }

  private final class KeyPostProcessor implements IdeEventQueue.EventDispatcher {
    @Override
    public boolean dispatch(@NotNull AWTEvent e) {
      if (isRecording() && e instanceof KeyEvent) {
        postProcessKeyEvent((KeyEvent)e);
      }
      return false;
    }

    public void postProcessKeyEvent(KeyEvent e) {
      if (e.getID() != KeyEvent.KEY_PRESSED) return;
      if (myLastActionInputEvent.contains(e)) {
        myLastActionInputEvent.remove(e);
        return;
      }
      final boolean modifierKeyIsPressed = e.getKeyCode() == KeyEvent.VK_CONTROL ||
                                           e.getKeyCode() == KeyEvent.VK_ALT ||
                                           e.getKeyCode() == KeyEvent.VK_META ||
                                           e.getKeyCode() == KeyEvent.VK_SHIFT;
      if (modifierKeyIsPressed) return;

      final boolean ready = IdeEventQueue.getInstance().getKeyEventDispatcher().isReady();
      final boolean isChar = UIUtil.isReallyTypedEvent(e);
      final boolean hasActionModifiers = e.isAltDown() || e.isControlDown() || e.isMetaDown();
      final boolean plainType = isChar && !hasActionModifiers;
      final boolean isEnter = e.getKeyCode() == KeyEvent.VK_ENTER;

      if (plainType && ready && !isEnter) {
        myRecordingMacro.appendKeyPressed(e.getKeyChar(), e.getKeyCode(), e.getModifiers());
        notifyUser(Character.valueOf(e.getKeyChar()).toString(), true);
      }
      else if ((!plainType && ready) || isEnter) {
        final String stroke = KeyStroke.getKeyStrokeForEvent(e).toString();

        final int pressed = stroke.indexOf("pressed");
        String key = stroke.substring(pressed + "pressed".length());
        String modifiers = stroke.substring(0, pressed);

        String shortcut = (modifiers.replaceAll("ctrl", "control").trim() + " " + key.trim()).trim();

        myRecordingMacro.appendShortcut(shortcut);
        notifyUser(KeymapUtil.getKeystrokeText(KeyStroke.getKeyStrokeForEvent(e)), false);
      }
    }
  }

  private void notifyUser(String text, boolean typing) {
    String actualText = text;
    if (typing) {
      int maxLength = TYPING_SAMPLE.length();
      myLastTyping += text;
      if (myLastTyping.length() > maxLength) {
        myLastTyping = "..." + myLastTyping.substring(myLastTyping.length() - maxLength);
      }
      actualText = myLastTyping;
    } else {
      myLastTyping = "";
    }

    if (myWidget != null) {
      myWidget.notifyUser(IdeBundle.message("status.bar.text.macro.recorded", actualText));
    }
  }
}
