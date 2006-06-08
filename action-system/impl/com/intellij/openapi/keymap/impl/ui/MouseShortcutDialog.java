package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import org.jetbrains.annotations.NonNls;

/**
 * @author Vladimir Kondratyev
 */
class MouseShortcutDialog extends DialogWrapper{
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.keymap.impl.ui.MouseShortcutDialog");

  private final Keymap myKeymap;
  private final String myActionId;
  private final Group myMainGroup;

  private final JRadioButton myRbSingleClick;
  private final JRadioButton myRbDoubleClick;
  private final JLabel myLblPreview;
  private final MyClickPad myClickPad;
  private final JTextArea myTarConflicts;

  private int myButton;
  private int myModifiers;

  /**
   * @param shortcut dialog will be initialized with this <code>shortcut</code>. It can be <code>null</code>
   * if dialog is used to create new mouse shortcut.
   */
  public MouseShortcutDialog(
    JComponent parentComponent,
    MouseShortcut shortcut,
    Keymap keymap,
    String actiondId,
    Group mainGroup
  ){
    super(parentComponent,true);
    setTitle(KeyMapBundle.message("mouse.shortcut.dialog.title"));

    LOG.assertTrue(keymap!=null);
    myKeymap=keymap;
    LOG.assertTrue(actiondId!=null);
    myActionId=actiondId;
    LOG.assertTrue(mainGroup!=null);
    myMainGroup=mainGroup;

    myRbSingleClick=new JRadioButton(KeyMapBundle.message("mouse.shortcut.dialog.single.click.radio"));
    myRbDoubleClick=new JRadioButton(KeyMapBundle.message("mouse.shortcut.dialog.double.click.radio"));
    ButtonGroup buttonGroup=new ButtonGroup();
    buttonGroup.add(myRbSingleClick);
    buttonGroup.add(myRbDoubleClick);

    myLblPreview=new JLabel(" ");

    myClickPad=new MyClickPad();

    myTarConflicts=new JTextArea();
    myTarConflicts.setFocusable(false);
    myTarConflicts.setEditable(false);
    myTarConflicts.setBackground(UIUtil.getPanelBackgound());
    myTarConflicts.setLineWrap(true);
    myTarConflicts.setWrapStyleWord(true);

    if(shortcut!=null){
      if(shortcut.getClickCount()==1){
        myRbSingleClick.setSelected(true);
      }else{
        myRbDoubleClick.setSelected(true);
      }
      myButton=shortcut.getButton();
      myModifiers=shortcut.getModifiers();
    }else{
      myRbSingleClick.setSelected(true);
      myButton=-1;
      myModifiers=-1;
    }

    updatePreviewAndConflicts();

    init();
  }

  /**
   * @return created/edited shortcut. Returns <code>null</code> if shortcut is invalid.
   */
  public MouseShortcut getMouseShortcut(){
    if(myButton!=-1 && myModifiers!=-1){
      return new MouseShortcut(myButton,myModifiers,myRbSingleClick.isSelected()?1:2);
    }else{
      return null;
    }
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected JComponent createCenterPanel(){
    JPanel panel=new JPanel(new GridBagLayout());

    // Single/Double click

    JPanel clickCountPanel=new JPanel(new GridBagLayout());
    clickCountPanel.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("mouse.shortcut.dialog.click.count.border")));
    panel.add(
      clickCountPanel,
      new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.HORIZONTAL,new Insets(0,0,4,0),0,0)
    );
    clickCountPanel.add(
      myRbSingleClick,
      new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,0,10),0,0)
    );
    clickCountPanel.add(
      myRbDoubleClick,
      new GridBagConstraints(1,0,1,1,1,0,GridBagConstraints.EAST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0)
    );

    ActionListener listener=new ActionListener(){
      public void actionPerformed(ActionEvent e){
        updatePreviewAndConflicts();
      }
    };
    myRbSingleClick.addActionListener(listener);
    myRbDoubleClick.addActionListener(listener);

    // Click pad

    JPanel clickPadPanel=new JPanel(new BorderLayout());
    panel.add(
      clickPadPanel,
      new GridBagConstraints(0,1,1,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,4,0),0,0)
    );
    clickPadPanel.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("mouse.shortcut.dialog.click.pad.border")));
    myClickPad.setPreferredSize(new Dimension(260,60));
    clickPadPanel.add(myClickPad,BorderLayout.CENTER);

    // Shortcut preview

    JPanel previewPanel=new JPanel(new GridBagLayout());
    previewPanel.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("mouse.shortcut.dialog.shortcut.preview.border")));
    panel.add(
      previewPanel,
      new GridBagConstraints(0,2,1,1,1,0,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,4,0),0,0)
    );
    previewPanel.add(
      myLblPreview,
      new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(2,2,2,2),0,0)
    );

    // Conflicts panel

    JPanel conflictsPanel=new JPanel(new GridBagLayout());
    conflictsPanel.setBorder(IdeBorderFactory.createTitledBorder(KeyMapBundle.message("mouse.shortcut.dialog.conflicts.border")));
    panel.add(
      conflictsPanel,
      new GridBagConstraints(0,3,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,0,0),0,0)
    );
    myTarConflicts.setPreferredSize(new Dimension(260,60));
    JScrollPane scrollPane=new JScrollPane(myTarConflicts);
    scrollPane.setBorder(null);
    conflictsPanel.add(
      scrollPane,
      new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.CENTER,GridBagConstraints.BOTH,new Insets(0,0,0,0),0,0)
    );

    return panel;
  }

  /**
   * Updates all UI controls
   */
  private void updatePreviewAndConflicts(){
    if(myButton==-1||myModifiers==-1){
      return;
    }

    myTarConflicts.setText(null);

    // Set text into preview area

    // empty string should have same height
    myLblPreview.setText(KeymapUtil.getMouseShortcutText(myButton,myModifiers,myRbSingleClick.isSelected()?1:2) + " ");

    // Detect conflicts

    final MouseShortcut mouseShortcut;
    if(myRbSingleClick.isSelected()){
      mouseShortcut=new MouseShortcut(myButton,myModifiers,1);
    }else{
      mouseShortcut=new MouseShortcut(myButton,myModifiers,2);
    }

    StringBuffer buffer = new StringBuffer();
    String[] actionIds = myKeymap.getActionIds(mouseShortcut);
    for(int i = 0; i < actionIds.length; i++) {
      String actionId = actionIds[i];
      if (actionId.equals(myActionId)){
        continue;
      }

      String actionPath = myMainGroup.getActionQualifiedPath(actionId);
      // actionPath == null for editor actions having corresponding $-actions
      if (actionPath == null){
        continue;
      }

      Shortcut[] shortcuts = myKeymap.getShortcuts(actionId);
      for (int j = 0; j < shortcuts.length; j++) {
        if (!(shortcuts[j] instanceof MouseShortcut)){
          continue;
        }

        MouseShortcut shortcut = (MouseShortcut)shortcuts[j];

        if (
          shortcut.getButton() != mouseShortcut.getButton() ||
          shortcut.getModifiers() != mouseShortcut.getModifiers()
        ){
          continue;
        }

        if(buffer.length() > 1) {
          buffer.append('\n');
        }
        buffer.append('[');
        buffer.append(actionPath);
        buffer.append(']');
        break;
      }
    }

    if (buffer.length() == 0) {
      myTarConflicts.setForeground(UIUtil.getTextAreaForeground());
      myTarConflicts.setText(KeyMapBundle.message("mouse.shortcut.dialog.no.conflicts.area"));
    }
    else {
      myTarConflicts.setForeground(Color.red);
      myTarConflicts.setText(KeyMapBundle.message("mouse.shortcut.dialog.assigned.to.area", buffer.toString()));
    }
  }

  private class MyClickPad extends JLabel{
    public MyClickPad(){
      super(
        KeyMapBundle.message("mouse.shortcut.label"),
        IconLoader.getIcon("/general/mouse.png"),
        JLabel.CENTER
      );
      // It's very imporatant that MouseListener is added to the Dialog. If you add
      // the same listener, for example, into the MyClickPad component you get fake
      // Alt and Meta modifiers. I means that pressing of middle button causes
      // Alt+Button2 event.
      // See bug ID 4109826 on Sun's bug parade.
      //cast is needed in order to compile with mustang
      MouseShortcutDialog.this.addMouseListener((MouseListener)new MouseAdapter(){
        public void mouseReleased(MouseEvent e){
          Component component= SwingUtilities.getDeepestComponentAt(e.getComponent(),e.getX(),e.getY());
          if(component== MyClickPad.this){
            e.consume();
            myButton=e.getButton();
            myModifiers=e.getModifiersEx();
            updatePreviewAndConflicts();
          }
        }
      });
    }
  }
}
