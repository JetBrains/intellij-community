package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class EditorNotificationPanel extends JPanel {

  protected final JLabel myLabel = new JLabel();
  private final JPanel myLinksPanel;

  public EditorNotificationPanel() {
    super(new BorderLayout());
    setBackground(LightColors.YELLOW);
    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
    add(myLabel, BorderLayout.CENTER);

    myLinksPanel = new JPanel(new FlowLayout());
    myLinksPanel.setBackground(LightColors.YELLOW);
    add(myLinksPanel, BorderLayout.EAST);
  }

  public void setText(String text) {
    myLabel.setText(text);
  }

  public void createActionLabel(final String text, @NonNls final String actionId) {
    HyperlinkLabel label = new HyperlinkLabel(text, Color.BLUE, LightColors.YELLOW, Color.BLUE);
    label.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          executeAction(actionId);
        }
      }
    });
    myLinksPanel.add(label);
  }

  protected void executeAction(final String actionId) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    final AnActionEvent event = new AnActionEvent(null, DataManager.getInstance().getDataContext(this), ActionPlaces.UNKNOWN,
                                                  action.getTemplatePresentation(), ActionManager.getInstance(),
                                                  0);
    action.beforeActionPerformedUpdate(event);
    action.update(event);

    if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
      action.actionPerformed(event);
    }
  }
}
