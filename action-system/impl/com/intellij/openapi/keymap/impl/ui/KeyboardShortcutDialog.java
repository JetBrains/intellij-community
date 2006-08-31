package com.intellij.openapi.keymap.impl.ui;


import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class KeyboardShortcutDialog extends DialogWrapper {
  private StrokePanel myFirstStrokePanel;
  private StrokePanel mySecondStrokePanel;
  private JCheckBox myEnableSecondKeystroke;
  private JLabel myKeystrokePreview;
  private JTextArea myConflictInfoArea;
  private Keymap myKeymap;
  private String myActionId;
  private Group myMainGroup;

  public KeyboardShortcutDialog(Component component, String actionId, Group mainGroup) {
    super(component, true);
    setTitle(KeyMapBundle.message("keyboard.shortcut.dialog.title"));
    myActionId = actionId;
    myMainGroup = mainGroup;
    myEnableSecondKeystroke = new JCheckBox(KeyMapBundle.message("enable.second.keystroke.check.box"));
    myEnableSecondKeystroke.setFocusable(false);
    myKeystrokePreview = new JLabel(" ");
    myConflictInfoArea = new JTextArea("");
    myConflictInfoArea.setFocusable(false);
    init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    // First stroke

    myFirstStrokePanel = new StrokePanel(KeyMapBundle.message("first.stroke.panel.title"));
    panel.add(
      myFirstStrokePanel,
      new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0)
    );

    // Second stroke panel

    panel.add(
      myEnableSecondKeystroke,
      new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,5,0),0,0)
    );

    mySecondStrokePanel = new StrokePanel(KeyMapBundle.message("second.stroke.panel.title"));
    panel.add(
      mySecondStrokePanel,
      new GridBagConstraints(0,2,1,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0)
    );

    // Shortcut preview

    JPanel previewPanel = new JPanel(new BorderLayout());
    previewPanel.setBorder(
      BorderFactory.createCompoundBorder(
        IdeBorderFactory.createTitledBorder(KeyMapBundle.message("shortcut.preview.ide.border.factory.title")),
        BorderFactory.createEmptyBorder(5,5,5,5)
      )
    );
    previewPanel.add(myKeystrokePreview);
    panel.add(
      previewPanel,
      new GridBagConstraints(0,3,1,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.HORIZONTAL,new Insets(0,0,5,0),0,0)
    );

    // Conflicts

    JPanel conflictsPanel = new JPanel(new BorderLayout());
    conflictsPanel.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("conflicts.ide.border.factory.title")));
    myConflictInfoArea.setEditable(false);
    myConflictInfoArea.setBackground(panel.getBackground());
    myConflictInfoArea.setLineWrap(true);
    myConflictInfoArea.setWrapStyleWord(true);
    final JScrollPane conflictInfoScroll = new JScrollPane(myConflictInfoArea);
    conflictInfoScroll.setPreferredSize(new Dimension(260, 60));
    conflictInfoScroll.setBorder(null);
    conflictsPanel.add(conflictInfoScroll);
    panel.add(
      conflictsPanel,
      new GridBagConstraints(0,4,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,0,0),0,0)
    );

    myEnableSecondKeystroke.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleSecondKey();
        updateCurrentKeyStrokeInfo();

        /** TODO[anton]????  */
        if (myEnableSecondKeystroke.isSelected()) {
          mySecondStrokePanel.getShortcutTextField().requestFocus();
        }
        else {
          myFirstStrokePanel.getShortcutTextField().requestFocus();
        }
      }
    });
    return panel;
  }

  public JComponent getPreferredFocusedComponent(){
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myFirstStrokePanel);
  }

  public void setData(Keymap keymap, KeyboardShortcut shortcut) {
    myKeymap = keymap;
    myEnableSecondKeystroke.setSelected(false);
    if (shortcut != null) {
      myFirstStrokePanel.getShortcutTextField().setKeyStroke(shortcut.getFirstKeyStroke());
      if (shortcut.getSecondKeyStroke() != null) {
        myEnableSecondKeystroke.setSelected(true);
        mySecondStrokePanel.getShortcutTextField().setKeyStroke(shortcut.getSecondKeyStroke());
      }
    }
    handleSecondKey();
    updateCurrentKeyStrokeInfo();
  }

  private void updateCurrentKeyStrokeInfo() {
    if (myConflictInfoArea == null || myKeystrokePreview == null){
      return;
    }

    myConflictInfoArea.setText(null);
    myKeystrokePreview.setText(" ");

    if (myKeymap == null){
      return;
    }

    KeyboardShortcut keyboardShortcut = getKeyboardShortcut();
    if (keyboardShortcut == null){
      return;
    }

    String strokeText = getTextByKeyStroke(keyboardShortcut.getFirstKeyStroke());
    String suffixText = getTextByKeyStroke(keyboardShortcut.getSecondKeyStroke());
    if(suffixText != null && suffixText.length() > 0) {
      strokeText += ',' + suffixText;
    }
    myKeystrokePreview.setText(strokeText);

    StringBuffer buffer = new StringBuffer();

    Map<String, ArrayList<KeyboardShortcut>> conflicts = myKeymap.getConflicts(myActionId, keyboardShortcut);

    Set<String> keys = conflicts.keySet();
    String[] actionIds = keys.toArray(new String[keys.size()]);
    for (String actionId : actionIds) {
      String actionPath = myMainGroup.getActionQualifiedPath(actionId);
      // actionPath == null for editor actions having corresponding $-actions
      if (actionPath == null) {
        continue;
      }
      if (buffer.length() > 1) {
        buffer.append('\n');
      }
      buffer.append('[');
      buffer.append(actionPath);
      buffer.append(']');
    }

    if (buffer.length() == 0) {
      myConflictInfoArea.setForeground(UIUtil.getTextAreaForeground());
      myConflictInfoArea.setText(KeyMapBundle.message("no.conflict.info.message"));
    }
    else {
      myConflictInfoArea.setForeground(Color.red);
      myConflictInfoArea.setText(KeyMapBundle.message("assigned.to.info.message", buffer.toString()));
    }
  }

  private void handleSecondKey() {
    mySecondStrokePanel.setEnabled(myEnableSecondKeystroke.isSelected());
  }

  public KeyboardShortcut getKeyboardShortcut() {
    KeyStroke firstStroke = myFirstStrokePanel.getKeyStroke();
    if (firstStroke == null) {
      return null;
    }
    KeyStroke secondStroke = myEnableSecondKeystroke.isSelected() ? mySecondStrokePanel.getKeyStroke() : null;
    return new KeyboardShortcut(firstStroke, secondStroke);
  }

  static String getTextByKeyStroke(KeyStroke keyStroke) {
    if(keyStroke == null) {
      return "";
    }
    return KeymapUtil.getKeystrokeText(keyStroke);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("preferences.keymap.shortcut");
  }

  private class StrokePanel extends JPanel {
    private final ShortcutTextField myShortcutTextField;

    public StrokePanel(String borderText) {
      setLayout(new BorderLayout());
      setBorder(
        BorderFactory.createCompoundBorder(
          IdeBorderFactory.createTitledBorder(borderText),
          BorderFactory.createEmptyBorder(5,5,5,5)
        )
      );

      myShortcutTextField = new ShortcutTextField(){
        protected void updateCurrentKeyStrokeInfo() {
          KeyboardShortcutDialog.this.updateCurrentKeyStrokeInfo();
        }
      };
      add(myShortcutTextField);
    }

    public ShortcutTextField getShortcutTextField() {
      return myShortcutTextField;
    }

    public void setEnabled(boolean state) {
      myShortcutTextField.setEnabled(state);
      repaint();
    }

    public KeyStroke getKeyStroke() {
      return myShortcutTextField.getKeyStroke();
    }
  }
}