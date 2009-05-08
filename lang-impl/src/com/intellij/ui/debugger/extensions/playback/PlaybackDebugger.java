package com.intellij.ui.debugger.extensions.playback;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.WaitFor;

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
    group.add(new RunAction());

    myComponent.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(), BorderLayout.NORTH);

    myText = new JTextArea();
    myComponent.add(new JScrollPane(myText), BorderLayout.CENTER);

    myComponent.add(myMessage, BorderLayout.SOUTH);
  }

  private class RunAction extends AnAction {

    private RunAction() {
      super("Run", "", IconLoader.getIcon("/general/run.png"));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myRunner == null);
    }

    public void actionPerformed(AnActionEvent e) {
      run();
    }
  }

  private void run() {
    assert myRunner == null;

    myMessage.setText("Waiting for IDE frame activation");
    myRunner = new PlaybackRunner(myText.getText() != null ? myText.getText() : "", this);


    new Thread() {
      @Override
      public void run() {
        new WaitFor() {
          protected boolean condition() {
            return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() instanceof IdeFrame;
          }
        };

        myMessage.setText("Starting script...");

        try {
          sleep(1000);
        }
        catch (InterruptedException e) {}



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
  }
}