package com.intellij.ui.debugger.extensions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class PlaybackDebugger implements UiDebuggerExtension, PlaybackRunner.StatusCallback {

  private JPanel myComponent = new JPanel(new BorderLayout());
  private JTextArea myText;

  private PlaybackRunner myRunner;

  private JLabel myMessage = new JLabel("", JLabel.LEFT);

  public PlaybackDebugger() {
    myComponent.setLayout(new BorderLayout());


    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RunOnFameActivationAction());
    group.add(new ActivateFrameAndRun());
    group.addSeparator();
    group.add(new StopAction());

    myComponent.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(), BorderLayout.NORTH);

    myText = new JTextArea();
    final String text = System.getProperty("idea.playback.script");
    if (text != null) {
      myText.setText(text);
    }
    myComponent.add(new JScrollPane(myText), BorderLayout.CENTER);

    myComponent.add(myMessage, BorderLayout.SOUTH);
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
    myMessage.setText("Waiting for IDE frame activation");
    myRunner = new PlaybackRunner(myText.getText() != null ? myText.getText() : "", this);


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

  public void error(final String text, int currentLine) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myMessage.setForeground(Color.red);
        myMessage.setText(text);
      }
    });
  }

  public void message(final String text, int currentLine) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myMessage.setForeground(UIManager.getColor("Label.foreground"));
        myMessage.setText(text);
      }
    });
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public String getName() {
    return "Playback";
  }

  public void dispose() {
    System.setProperty("idea.playback.script", myText.getText());
  }
}