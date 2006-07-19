package com.intellij.openapi.keymap.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ui.KeyboardShortcutDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.wm.impl.IdeFrame;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * This class is automaton with finite number of state.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IdeKeyEventDispatcher implements Disposable {
  @NonNls
  private static final String GET_CACHED_STROKE_METHOD_NAME = "getCachedStroke";

  private static final int STATE_INIT = 0;
  private static final int STATE_WAIT_FOR_SECOND_KEYSTROKE = 1;
  private static final int STATE_SECOND_STROKE_IN_PROGRESS = 2;
  private static final int STATE_PROCESSED = 3;

  private KeyStroke myFirstKeyStroke;
  /**
   * When we "dispatch" key event via keymap, i.e. when registered action has been executed
   * instead of event dispatching, then we have to consume all following KEY_RELEASED and
   * KEY_TYPED event because they are not valid.
   */
  private boolean myPressedWasProcessed;
  private int myState;

  private final ArrayList<AnAction> myActions;
  private final PresentationFactory myPresentationFactory;
  private JComponent myFoundComponent;
  private boolean myDisposed = false;

  public IdeKeyEventDispatcher(){
    myState=STATE_INIT;
    myActions=new ArrayList<AnAction>();
    myPresentationFactory=new PresentationFactory();
    Disposer.register(ApplicationManager.getApplication(), this);
  }

  public boolean isWaitingForSecondKeyStroke(){
    return myState == STATE_WAIT_FOR_SECOND_KEYSTROKE || myPressedWasProcessed;
  }

  /**
   * @return <code>true</code> if and only if the passed event is already dispatched by the
   * <code>IdeKeyEventDispatcher</code> and there is no need for any other processing of the event.
   */
  public boolean dispatchKeyEvent(final KeyEvent e){
    if (myDisposed) return false;

    if(e.isConsumed()){
      return false;
    }

    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();

    // shortcuts should not work in shortcut setup fields
    if (focusOwner instanceof KeyboardShortcutDialog.ShortcutTextField) {
      return false;
    }

    MenuSelectionManager menuSelectionManager=MenuSelectionManager.defaultManager();
    MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
    if(selectedPath.length>0){
      if (!(selectedPath[0] instanceof ComboPopup)) {
        // The following couple of lines of code is a PATCH!!!
        // It is needed to ignore ENTER KEY_TYPED events which sometimes can reach editor when an action
        // is invoked from main menu via Enter key.
        myState=STATE_PROCESSED;
        myPressedWasProcessed=true;
        return false;
      }
    }

    // Keymap shortcuts (i.e. not local shortcuts) should work only in:
    // - main frame
    // - floating focusedWindow
    // - when there's an editor in contexts
    Window focusedWindow = focusManager.getFocusedWindow();
    boolean isModalContext = focusedWindow != null && isModalContext(focusedWindow);

    DataContext dataContext = DataManager.getInstance().getDataContext();

    if (myState == STATE_INIT) {
      return inInitState(focusOwner, e, isModalContext, dataContext);
    } else if (myState == STATE_PROCESSED) {
      return inProcessedState(focusOwner, e, isModalContext, dataContext);
    } else if (myState == STATE_WAIT_FOR_SECOND_KEYSTROKE) {
      return inWaitForSecondStrokeState(e, isModalContext, dataContext);
    } else if (myState == STATE_SECOND_STROKE_IN_PROGRESS) {
      return inSecondStrokeInProgressState(e, isModalContext, dataContext);
    } else {
      throw new IllegalStateException("state = " + myState);
    }
  }

  /**
   * @return <code>true</code> if and only if the <code>component</code> represents
   * modal context.
   * @throws java.lang.IllegalArgumentException if <code>component</code> is <code>null</code>.
   */
  public static boolean isModalContext(Component component) {
    if(component==null){
      throw new IllegalArgumentException("component cannot be null");
    }
    Window window;
    if (component instanceof Window) {
      window = (Window)component;
    } else {
      window = SwingUtilities.getWindowAncestor(component);
    }
    boolean isMainFrame = window instanceof IdeFrame;
    boolean isFloatingDecorator = window instanceof FloatingDecorator;
    return !isMainFrame && !isFloatingDecorator;
  }

  private boolean inWaitForSecondStrokeState(KeyEvent e, boolean isModalContext, DataContext dataContext) {
    // a key pressed means that the user starts to enter the second stroke...
    if (KeyEvent.KEY_PRESSED==e.getID()) {
      myState=STATE_SECOND_STROKE_IN_PROGRESS;
      return inSecondStrokeInProgressState(e, isModalContext, dataContext);
    }
    // looks like RELEASEs (from the first stroke) go here...  skip them
    return true;
  }

  /**
   * This is hack. AWT doesn't allow to create KeyStroke with specified key code and key char
   * simultaneously. Therefore we are using reflection.
   */
  private KeyStroke getKeyStrokeWithoutMouseModifiers(KeyStroke originalKeyStroke){
    int modifier=originalKeyStroke.getModifiers()&~InputEvent.BUTTON1_DOWN_MASK&~InputEvent.BUTTON1_MASK&
                 ~InputEvent.BUTTON2_DOWN_MASK&~InputEvent.BUTTON2_MASK&
                 ~InputEvent.BUTTON3_DOWN_MASK&~InputEvent.BUTTON3_MASK;
    try {
      Method[] methods=AWTKeyStroke.class.getDeclaredMethods();
      Method getCachedStrokeMethod=null;
      for(int i=0;i<methods.length;i++){
        Method method=methods[i];
        if(GET_CACHED_STROKE_METHOD_NAME.equals(method.getName())){
          getCachedStrokeMethod=method;
          getCachedStrokeMethod.setAccessible(true);
          break;
        }
      }
      if(getCachedStrokeMethod==null){
        throw new IllegalStateException("not found method with name getCachedStrokeMethod");
      }
      Object[] getCachedStrokeMethodArgs=new Object[]{
        new Character(originalKeyStroke.getKeyChar()),
        new Integer(originalKeyStroke.getKeyCode()),
        new Integer(modifier),
        new Boolean(originalKeyStroke.isOnKeyRelease())
      };
      KeyStroke keyStroke=(KeyStroke)getCachedStrokeMethod.invoke(
        originalKeyStroke,
        getCachedStrokeMethodArgs
      );
      return keyStroke;
    }catch(Exception exc){
      throw new IllegalStateException(exc.getMessage());
    }
  }

  private boolean inSecondStrokeInProgressState(KeyEvent e, boolean isModalContext, DataContext dataContext) {
    // when any key is released, we stop waiting for the second stroke
    if(KeyEvent.KEY_RELEASED==e.getID()){
      myFirstKeyStroke=null;
      myState=STATE_INIT;
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      WindowManager.getInstance().getStatusBar(project).setInfo(null);
      return false;
    }

    KeyStroke originalKeyStroke=KeyStroke.getKeyStrokeForEvent(e);
    KeyStroke keyStroke=getKeyStrokeWithoutMouseModifiers(originalKeyStroke);

    fillActionsList(myFoundComponent, myFirstKeyStroke, keyStroke, isModalContext);

    // consume the wrong second stroke and keep on waiting
    if (myActions.size() == 0) {
      return true;
    }

    // finally user had managed to enter the second keystroke, so let it be processed
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    StatusBarEx statusBar = (StatusBarEx) WindowManager.getInstance().getStatusBar(project);
    if (processAction(e, dataContext)) {
      statusBar.setInfo(null);
      return true;
    } else {
      return false;
    }
  }

  private boolean inProcessedState(Component focusOwner, final KeyEvent e, boolean isModalContext, DataContext dataContext) {
    // ignore typed events which come after processed pressed event
    if (KeyEvent.KEY_TYPED==e.getID() && myPressedWasProcessed) {
      return true;
    }
    myState = STATE_INIT;
    myPressedWasProcessed = false;
    return inInitState(focusOwner, e, isModalContext, dataContext);
  }

  private boolean inInitState(Component focusOwner, KeyEvent e, boolean isModalContext, DataContext dataContext) {
    KeyStroke originalKeyStroke=KeyStroke.getKeyStrokeForEvent(e);
    KeyStroke keyStroke=getKeyStrokeWithoutMouseModifiers(originalKeyStroke);

    boolean hasSecondStroke = fillActionsList(focusOwner, keyStroke, null, isModalContext);

    if(myActions.size() == 0) {
      if (SystemInfo.isMac) {
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getModifiersEx() == KeyEvent.ALT_DOWN_MASK && hasMnemonicInWindow(focusOwner, e.getKeyCode())) {
          myPressedWasProcessed = true;
          myState = STATE_PROCESSED;
          return false;
        }
      }

      // there's nothing mapped for this stroke
      return false;
    }

    if(hasSecondStroke){
      myFirstKeyStroke=keyStroke;
      ArrayList<Pair<AnAction, KeyStroke>> secondKeyStorkes = new ArrayList<Pair<AnAction,KeyStroke>>();
      for (int i = 0; i < myActions.size(); i++) {
        AnAction action = myActions.get(i);
        Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
        for (int j = 0; j < shortcuts.length; j++) {
          Shortcut shortcut = shortcuts[j];
          if (shortcut instanceof KeyboardShortcut) {
            KeyboardShortcut keyShortcut = (KeyboardShortcut)shortcut;
            if (keyShortcut.getFirstKeyStroke().equals(myFirstKeyStroke)) {
              secondKeyStorkes.add(new Pair<AnAction, KeyStroke>(action, keyShortcut.getSecondKeyStroke()));
            }
          }
        }
      }

      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      StringBuffer message = new StringBuffer();
      message.append(KeyMapBundle.message("prefix.key.pressed.message"));
      message.append(' ');
      for (int i = 0; i < secondKeyStorkes.size(); i++) {
        Pair<AnAction, KeyStroke> pair = secondKeyStorkes.get(i);
        if (i > 0) message.append(", ");
        message.append(pair.getFirst().getTemplatePresentation().getText());
        message.append(" (");
        message.append(KeymapUtil.getKeystrokeText(pair.getSecond()));
        message.append(")");
      }

      WindowManager.getInstance().getStatusBar(project).setInfo(message.toString());
      myState=STATE_WAIT_FOR_SECOND_KEYSTROKE;
      return true;
    }else{
      if (processAction(e, dataContext)) {
        return true;
      } else {
        return false;
      }
    }
  }

  private boolean hasMnemonicInWindow(Component focusOwner, int keyCode) {
    if (keyCode == KeyEvent.VK_ALT || keyCode == 0) return false; // Optimization
    final Container container = getContainer(focusOwner);
    return hasMnemonic(container, keyCode);
  }

  private Container getContainer(final Component focusOwner) {
    if (focusOwner.isLightweight()) {
      Container container = focusOwner.getParent();
      while (container != null) {
        final Container parent = container.getParent();
        if (parent instanceof JLayeredPane) break;
        if (parent != null && parent.isLightweight()) {
          container = parent;
        }
        else {
          break;
        }
      }
      return container;
    }

    return SwingUtilities.windowForComponent(focusOwner);
  }

  private boolean hasMnemonic(final Container container, final int keyCode) {
    if (container == null) return false;

    final Component[] components = container.getComponents();
    for (Component component : components) {
      if (component instanceof AbstractButton) {
        final AbstractButton button = (AbstractButton)component;
        if (button.getMnemonic() == keyCode) return true;
      }
      if (component instanceof Container) {
        if (hasMnemonic((Container)component, keyCode)) return true;
      }
    }
    return false;
  }

  private boolean processAction(final KeyEvent e, DataContext dataContext) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    for (int i=0; i < myActions.size(); i++) {
      final AnAction action = myActions.get(i);
      final Presentation presentation = myPresentationFactory.getPresentation(action);

      // Mouse modifiers are 0 because they have no any sense when action is invoked via keyboard
      final AnActionEvent actionEvent = new AnActionEvent(e, dataContext, ActionPlaces.MAIN_MENU, presentation,
                                                          ActionManager.getInstance(),
                                                          0);
      action.beforeActionPerformedUpdate(actionEvent);
      if (!presentation.isEnabled()) {
        continue;
      }

      myState = STATE_PROCESSED;
      myPressedWasProcessed = e.getID() == KeyEvent.KEY_PRESSED;

      ((DataManagerImpl.MyDataContext)dataContext).setEventCount(IdeEventQueue.getInstance().getEventCount());
      actionManager.fireBeforeActionPerformed(action, actionEvent.getDataContext());
      Component component = (Component)actionEvent.getDataContext().getData(DataConstantsEx.CONTEXT_COMPONENT);
      if (component != null && !component.isShowing()) {
        return true;
      }
      e.consume();
      action.actionPerformed(actionEvent);
      return true;
    }

    return false;
  }

  /**
   * This method fills <code>myActions</code> list.
   * @return true if there is a shortcut with second stroke found.
   */
  private boolean fillActionsList(Component component, KeyStroke firstKeyStroke, KeyStroke secondKeyStroke, boolean isModalContext){
    myFoundComponent = null;
    myActions.clear();

    boolean hasSecondStroke = false;

    // here we try to find "local" shortcuts

    for (; component != null; component = component.getParent()) {
      if (!(component instanceof JComponent)) {
        continue;
      }
      ArrayList listOfActions = (ArrayList)((JComponent)component).getClientProperty(AnAction.ourClientProperty);
      if (listOfActions == null) {
        continue;
      }
      for (int i=0; i < listOfActions.size(); i++) {
        Object o = listOfActions.get(i);
        if (!(o instanceof AnAction)) {
          continue;
        }
        AnAction action = (AnAction)o;
        hasSecondStroke |= addAction(action, firstKeyStroke, secondKeyStroke);
      }
      // once we've found a proper local shortcut(s), we continue with non-local shortcuts
      if (myActions.size() > 0) {
        myFoundComponent = (JComponent)component;
        break;
      }
    }

    // search in main keymap

    String[] actionIds;
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    if (secondKeyStroke == null) {
      actionIds = keymap.getActionIds(firstKeyStroke);
    }
    else {
      actionIds = keymap.getActionIds(firstKeyStroke, secondKeyStroke);
    }

    ActionManager actionManager = ActionManager.getInstance();
    for (int i = 0; i < actionIds.length; i++) {
      String actionId = actionIds[i];
      AnAction action = actionManager.getAction(actionId);
      if (action != null){
        if (isModalContext && !action.isEnabledInModalContext()){
          continue;
        }
        hasSecondStroke |= addAction(action, firstKeyStroke, secondKeyStroke);
      }
    }
    return hasSecondStroke;
  }

  /**
   * @return true if action is added and has second stroke
   */
  private boolean addAction(AnAction action, KeyStroke firstKeyStroke, KeyStroke secondKeyStroke) {
    boolean hasSecondStroke = false;

    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    for (int j = 0; j < shortcuts.length; j++) {
      Shortcut shortcut = shortcuts[j];
      if (!(shortcut instanceof KeyboardShortcut)) {
        continue;
      }
      KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
      if (
        firstKeyStroke.equals(keyboardShortcut.getFirstKeyStroke()) &&
        (secondKeyStroke == null || secondKeyStroke.equals(keyboardShortcut.getSecondKeyStroke()))
      ) {
        if (!myActions.contains(action)) {
          myActions.add(action);
        }

        if (keyboardShortcut.getSecondKeyStroke() != null) {
          hasSecondStroke = true;
        }
      }
    }

    return hasSecondStroke;
  }

  public void dispose() {
    myDisposed = true;
  }
}