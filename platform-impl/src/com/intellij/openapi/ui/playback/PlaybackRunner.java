package com.intellij.openapi.ui.playback;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.text.StringTokenizer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class PlaybackRunner {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.PlaybackRunner");

  private Robot myRobot;

  private String myScript;
  private StatusCallback myCallback;

  private ArrayList<Command> myCommands = new ArrayList<Command>();
  private ActionCallback myActionCallback;
  private boolean myStopRequested;

  public PlaybackRunner(String script, StatusCallback callback) {
    myScript = script;
    myCallback = callback;
  }

  public ActionCallback run() {
    myStopRequested = false;

    try {
      myActionCallback = new ActionCallback();

      myRobot = new Robot();

      parse();

      new Thread() {
        @Override
        public void run() {
          executeFrom(0);
        }
      }.start();

    }
    catch (AWTException e) {
      LOG.error(e);
    }

    return myActionCallback;
  }

  private void executeFrom(final int cmdIndex) {
    if (cmdIndex < myCommands.size()) {
      final Command cmd = myCommands.get(cmdIndex);
      if (myStopRequested) {
        myCallback.message("Stopped", cmdIndex);
        myActionCallback.setRejected();
        return;
      }
      cmd.execute(myCallback, myRobot).doWhenDone(new Runnable() {
        public void run() {
          executeFrom(cmdIndex + 1);
        }
      }).doWhenRejected(new Runnable() {
        public void run() {
          myActionCallback.setRejected();
        }
      });
    }
    else {
      myCallback.message("Finished", myCommands.size());
      myActionCallback.setDone();
    }
  }

  private void parse() {
    final StringTokenizer tokens = new StringTokenizer(myScript, "\n");
    int line = 0;
    while (tokens.hasMoreTokens()) {
      final String eachLine = tokens.nextToken();
      final Command cmd = createCommand(eachLine, line++);
      myCommands.add(cmd);

    }
  }

  private Command createCommand(String string, int line) {
    if (string.startsWith(AbstractCommand.CMD_PREFIX + AbstractCommand.CMD_PREFIX)) {
      return new EmptyCommand(line);
    }

    if (string.startsWith(DelayCommand.PREFIX)) {
      return new DelayCommand(string, line);
    }

    if (string.startsWith(KeyShortcut.PREFIX)) {
      return new KeyShortcut(string, line);
    }

    if (string.startsWith(Action.PREFIX)) {
      return new Action(string, line);
    }

    return new AlphaNumericType(string, line);
  }

  private void setDone() {
    myActionCallback.setDone();
  }

  public void stop() {
    myStopRequested = true;
  }

  public interface StatusCallback {
    void error(String text, int currentLine);

    void message(String text, int currentLine);

    public abstract static class Edt implements StatusCallback {
      public final void error(final String text, final int currentLine) {
        if (SwingUtilities.isEventDispatchThread()) {
          errorEdt(text, currentLine);
        } else {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              errorEdt(text, currentLine);
            }
          });
        }
      }

      public abstract void errorEdt(String text, int curentLine);

      public final void message(final String text, final int currentLine) {
        if (SwingUtilities.isEventDispatchThread()) {
          messageEdt(text, currentLine);
        } else {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              messageEdt(text, currentLine);
            }
          });
        }
      }

      public abstract void messageEdt(String text, int curentLine);
    }
  }

  private interface Command {
    ActionCallback execute(StatusCallback cb, Robot robot);
  }

  private static abstract class AbstractCommand implements Command {

    static String CMD_PREFIX = "%";

    private String myText;
    private int myLine;

    protected AbstractCommand(String text, int line) {
      myText = text != null ? text : null;
      myLine = line;
    }

    public String getText() {
      return myText;
    }

    public int getLine() {
      return myLine;
    }

    public final ActionCallback execute(StatusCallback cb, Robot robot) {
      try {
        dumpCommand(cb);
        return _execute(cb, robot);
      }
      catch (Exception e) {
        cb.error(e.getMessage(), getLine());
        return new ActionCallback.Rejected();
      }
    }

    protected abstract ActionCallback _execute(StatusCallback cb, Robot robot);

    public void dumpCommand(final StatusCallback cb) {
      cb.message(getText(), getLine());
    }

    public void dumpError(final StatusCallback cb, final String text) {
      cb.error(text, getLine());
    }
  }

  private static class EmptyCommand extends AbstractCommand {
    private EmptyCommand(int line) {
      super("", line);
    }

    public ActionCallback _execute(StatusCallback cb, Robot robot) {
      return new ActionCallback.Done();
    }
  }

  private static class ErrorCommand extends AbstractCommand {

    private ErrorCommand(String text, int line) {
      super(text, line);
    }

    public ActionCallback _execute(StatusCallback cb, Robot robot) {
      dumpError(cb, getText());
      return new ActionCallback.Rejected();
    }
  }

  private static class Action extends AbstractCommand {

    static String PREFIX = CMD_PREFIX + "action";

    private Action(String text, int line) {
      super(text, line);
    }

    protected ActionCallback _execute(StatusCallback cb, Robot robot) {
      final String actionName = getText().substring(PREFIX.length()).trim();

      final AnAction action = ActionManager.getInstance().getAction(actionName);
      if (action == null) {
        dumpError(cb, "Unknown action: " + actionName);
        return new ActionCallback.Rejected();
      }

      final InputEvent input = getInputEvent(actionName);

      final ActionCallback result = new ActionCallback();

      robot.delay(Registry.intValue("actionSystem.playback.autodelay")); 
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          ActionManager.getInstance().tryToExecute(action, input, null, null, false).notifyWhenDone(result);
        }
      });

      return result;
    }

    private InputEvent getInputEvent(String actionName) {
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionName);
      KeyStroke keyStroke = null;
      for (Shortcut each : shortcuts) {
        if (each instanceof KeyboardShortcut) {
          keyStroke = ((KeyboardShortcut)each).getFirstKeyStroke();
          if (keyStroke != null) break;
        }
      }

      if (keyStroke != null) {
        return new KeyEvent(JOptionPane.getRootFrame(),
                                               KeyEvent.KEY_PRESSED,
                                               System.currentTimeMillis(),
                                               keyStroke.getModifiers(),
                                               keyStroke.getKeyCode(),
                                               keyStroke.getKeyChar(),
                                               KeyEvent.KEY_LOCATION_STANDARD);
      } else {
        return new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1);
      }


    }
  }

  private static class DelayCommand extends AbstractCommand {
    static String PREFIX = CMD_PREFIX + "delay";

    private DelayCommand(String text, int line) {
      super(text, line);
    }

    public ActionCallback _execute(StatusCallback cb, Robot robot) {
      final String s = getText().substring(PREFIX.length()).trim();

      try {
        final Integer delay = Integer.valueOf(s);
        robot.delay(delay.intValue());
      }
      catch (NumberFormatException e) {
        dumpError(cb, "Invalid delay value: " + s);
        return new ActionCallback.Rejected();
      }

      return new ActionCallback.Done();
    }
  }

  private abstract static class TypeCommand extends AbstractCommand {

    private KeyStokeMap myMap = new KeyStokeMap();

    private TypeCommand(String text, int line) {
      super(text, line);
    }

    protected void type(Robot robot, int code, int modfiers) {
      type(robot, KeyStroke.getKeyStroke(code, modfiers));
    }

    protected void type(Robot robot, KeyStroke keyStroke) {
      boolean shift = (keyStroke.getModifiers() & KeyEvent.SHIFT_MASK) > 0;
      boolean alt = (keyStroke.getModifiers() & KeyEvent.ALT_MASK) > 0;
      boolean control = (keyStroke.getModifiers() & KeyEvent.ALT_MASK) > 0;
      boolean meta = (keyStroke.getModifiers() & KeyEvent.META_MASK) > 0;

      if (shift) {
        robot.keyPress(KeyEvent.VK_SHIFT);
      }

      if (control) {
        robot.keyPress(KeyEvent.VK_CONTROL);
      }

      if (alt) {
        robot.keyPress(KeyEvent.VK_ALT);
      }

      if (meta) {
        robot.keyPress(KeyEvent.VK_META);
      }

      robot.keyPress(keyStroke.getKeyCode());
      robot.delay(Registry.intValue("actionSystem.playback.autodelay"));
      robot.keyRelease(keyStroke.getKeyCode());

      if (shift) {
        robot.keyRelease(KeyEvent.VK_SHIFT);
      }

      if (control) {
        robot.keyRelease(KeyEvent.VK_CONTROL);
      }

      if (alt) {
        robot.keyRelease(KeyEvent.VK_ALT);
      }

      if (meta) {
        robot.keyRelease(KeyEvent.VK_META);
      }
    }

    protected KeyStroke get(char c) {
      return myMap.get(c);
    }

    protected KeyStroke getFromShortcut(String sc) {
      return myMap.get(sc);
    }
  }

  private static class KeyShortcut extends TypeCommand {

    public static String PREFIX = CMD_PREFIX + "[";
    public static String POSTFIX = CMD_PREFIX + "]";

    private KeyShortcut(String text, int line) {
      super(text, line);
    }

    public ActionCallback _execute(StatusCallback cb, Robot robot) {
      final String one = getText().substring(PREFIX.length());
      if (!one.endsWith("]")) {
        dumpError(cb, "Expected " + "]");
        return new ActionCallback.Rejected();
      }

      type(robot, getFromShortcut(one.substring(0, one.length() - 1).trim()));

      return new ActionCallback.Done();
    }
  }

  private static class AlphaNumericType extends TypeCommand {

    private AlphaNumericType(String text, int line) {
      super(text, line);
    }

    public ActionCallback _execute(StatusCallback cb, Robot robot) {
      final String text = getText();
      for (int i = 0; i < text.length(); i++) {
        final char each = text.charAt(i);
        if ('\\' == each && i + 1 < text.length()) {
          final char next = text.charAt(i + 1);
          boolean processed = true;
          switch (next) {
            case 'n':
              type(robot, KeyEvent.VK_ENTER, 0);
              break;
            case 't':
              type(robot, KeyEvent.VK_TAB, 0);
              break;
            case 'r':
              type(robot, KeyEvent.VK_ENTER, 0);
              break;
            default:
              processed = false;
          }

          if (processed) {
            i++;
            continue;
          }
        }
        type(robot, get(each));
      }
      return new ActionCallback.Done();
    }
  }

  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());
    frame.getContentPane().add(content, BorderLayout.CENTER);

    final JTextArea textArea = new JTextArea();
    content.add(textArea, BorderLayout.CENTER);

    frame.setBounds(300, 300, 300, 300);
    frame.show();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        textArea.requestFocus();
        start();
      }
    });
  }
  
  private static void start() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
      public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getID()) {
          case KeyEvent.KEY_PRESSED:
            break;
          case KeyEvent.KEY_RELEASED:
            break;
          case KeyEvent.KEY_TYPED:
            break;
        }

        return false;
      }
    });

    new PlaybackRunner("%[comma]", new StatusCallback() {
      public void error(String text, int currentLine) {
        System.out.println("Error: " + currentLine + " " + text);
      }

      public void message(String text, int currentLine) {
        System.out.println(currentLine + " " + text);
      }
    }).run();
  }


}