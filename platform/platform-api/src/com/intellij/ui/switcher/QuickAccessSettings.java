/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.util.ui.AwtVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.util.*;

public class QuickAccessSettings implements ApplicationComponent, SearchableConfigurable, KeymapManagerListener, Disposable {

  private Set<Integer> myModifierVks = new HashSet<Integer>();
  private Keymap myKeymap;
  @NonNls public static final String SWITCH_UP = "SwitchUp";
  @NonNls public static final String SWITCH_DOWN = "SwitchDown";
  @NonNls public static final String SWITCH_LEFT = "SwitchLeft";
  @NonNls public static final String SWITCH_RIGHT = "SwitchRight";
  @NonNls public static final String SWITCH_APPLY = "SwitchApply";

  private QuickAccessSettings.Ui myUi;
  private RegistryValue myModifiersValue;

  @NotNull
  public String getComponentName() {
    return "QuickAccess";
  }

  public void initComponent() {
    myModifiersValue = Registry.get("actionSystem.quickAccessModifiers");
    myModifiersValue.addListener(new RegistryValueListener.Adapter() {
      public void afterValueChanged(RegistryValue value) {
        applyModifiersFromRegistry();
      }
    }, this);

    KeymapManager kmMgr = KeymapManager.getInstance();
    kmMgr.addKeymapManagerListener(this);

    activeKeymapChanged(kmMgr.getActiveKeymap());

    applyModifiersFromRegistry();
  }
      
  public void disposeComponent() {
    KeymapManager.getInstance().removeKeymapManagerListener(this);
    Disposer.dispose(this);
  }

  public void dispose() {
  }

  @Nls
  public String getDisplayName() {
    return "Quick Access";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    myUi = new Ui();
    return myUi;
  }

  public String getId() {
    return "QuickAccess";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public void activeKeymapChanged(Keymap keymap) {
    KeymapManager mgr = KeymapManager.getInstance();
    myKeymap = mgr.getActiveKeymap();
  }

  private void saveModifiersToRegistry(Set<String> codeTexts) {
    StringBuffer value = new StringBuffer();
    for (String each : codeTexts) {
      if (value.length() > 0) {
        value.append(" ");
      }
      value.append(each);
    }
    myModifiersValue.setValue(value.toString());
  }

  private void applyModifiersFromRegistry() {
    Application app = ApplicationManager.getApplication();
    if (app != null && app.isUnitTestMode()) return;

    String text = getModifierRegistryValue();
    String[] vks = text.split(" ");

    HashSet<String> vksSet = new HashSet<String>();
    vksSet.addAll(Arrays.asList(vks));
    myModifierVks.clear();
    int mask = getModiferMask(vksSet);
    myModifierVks.addAll(getModifiersVKs(mask));

    reassignActionShortcut(SWITCH_UP, mask, KeyEvent.VK_UP);
    reassignActionShortcut(SWITCH_DOWN, mask, KeyEvent.VK_DOWN);
    reassignActionShortcut(SWITCH_LEFT, mask, KeyEvent.VK_LEFT);
    reassignActionShortcut(SWITCH_RIGHT, mask, KeyEvent.VK_RIGHT);
    reassignActionShortcut(SWITCH_APPLY, mask, KeyEvent.VK_ENTER);
  }

  private String getModifierRegistryValue() {
    String value = myModifiersValue.asString().trim();
    if (value.length() > 0) return value;

    if (SystemInfo.isMac) {
      return "control alt";
    } else {
      return "shift alt";
    }
  }

  private void reassignActionShortcut(String actionId, int modifiers, int actionCode) {
    removeShortcuts(actionId);
    if (modifiers > 0) {
      myKeymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(actionCode, modifiers), null));
    }
  }

  private void removeShortcuts(String actionId) {
    Shortcut[] shortcuts = myKeymap.getShortcuts(actionId);
    for (Shortcut each : shortcuts) {
      if (each instanceof KeyboardShortcut) {
          myKeymap.removeShortcut(actionId, each);
      }
    }
  }

  private int getModiferMask(Set<String> codeTexts) {
    int mask = 0;
    for (String each : codeTexts) {
      if ("control".equals(each)) {
        mask |= KeyEvent.CTRL_MASK;
      } else if ("shift".equals(each)) {
        mask |= KeyEvent.SHIFT_MASK;
      } else if ("alt".equals(each)) {
        mask |= KeyEvent.ALT_MASK;
      } else if ("meta".equals(each)) {
        mask |= KeyEvent.META_MASK;
      }
    }

    return mask;
  }

  public static Set<Integer> getModifiersVKs(int mask) {
    Set<Integer> codes = new HashSet<Integer>();
    if ((mask & KeyEvent.SHIFT_MASK) > 0) {
      codes.add(KeyEvent.VK_SHIFT);
    }
    if ((mask & KeyEvent.CTRL_MASK) > 0) {
      codes.add(KeyEvent.VK_CONTROL);
    }

    if ((mask & KeyEvent.META_MASK) > 0) {
      codes.add(KeyEvent.VK_META);
    }

    if ((mask & KeyEvent.ALT_MASK) > 0) {
      codes.add(KeyEvent.VK_ALT);
    }

    return codes;
  }

  private Set<String> getModifierTexts() {
    HashSet<String> result = new HashSet<String>();

    for (Integer each : myModifierVks) {
      if (each == KeyEvent.VK_SHIFT) {
        result.add("shift");
      }
      else if (each == KeyEvent.VK_CONTROL) {
        result.add("control");
      }
      else if (each == KeyEvent.VK_ALT) {
        result.add("alt");
      }
      else if (each == KeyEvent.VK_META) {
        result.add("meta");
      }
    }

    return result;
  }


  public boolean isModified() {
    return !myUi.getModifiers().equals(getModifierTexts()) || isEnabled() != myUi.isQaEnabled() || getHoldTime() != myUi.getHoldTime();
  }

  public void apply() throws ConfigurationException {
    Registry.get("actionSystem.quickAccessEnabled").setValue(myUi.isEnabled());
    saveModifiersToRegistry(myUi.getModifiers());
    Registry.get("actionSystem.keyGestureHoldTime").setValue(myUi.getHoldTime());
  }

  public void reset() {
    myUi.reset(isEnabled(), getModifierTexts(), getHoldTime());
  }

  private int getHoldTime() {
    return Registry.intValue("actionSystem.keyGestureHoldTime");
  }

  public void disposeUIResources() {
    myUi = null;
  }

  public static QuickAccessSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(QuickAccessSettings.class);
  }

  public boolean isEnabled() {
    return Registry.is("actionSystem.quickAccessEnabled");
  }

  public Set<Integer> getModiferCodes() {
    return myModifierVks;
  }

  private class Ui extends JPanel {

    private Set<String> myModifiers = new HashSet<String>();
    private boolean myQaEnabled;
    private int myDelay;

    private VerticalBox myBox = new VerticalBox();

    private JCheckBox myEnabled;

    private ModifierBox myCtrl;
    private ModifierBox myAlt;
    private ModifierBox myShift;
    private ModifierBox myMeta;
    private JPanel myConflicts;
    private JFormattedTextField myHoldTime;

    

    private Ui() {
      JPanel north = new JPanel(new BorderLayout());
      north.add(myBox, BorderLayout.NORTH);

      setLayout(new BorderLayout());
      add(north, BorderLayout.WEST);

      myEnabled = new JCheckBox("Enable Quick Access");
      myEnabled.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          myQaEnabled = myEnabled.isSelected();
          processEnabled();
        }
      });
      myBox.add(myEnabled);

      VerticalBox kbConfig = new VerticalBox();

      JPanel modifiers = new JPanel(new FlowLayout(FlowLayout.CENTER)) {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          size.width *= 1.5;
          return size;
        }
      };
      myCtrl = new ModifierBox("control", KeyEvent.getKeyModifiersText(KeyEvent.CTRL_MASK));
      myAlt = new ModifierBox("alt", KeyEvent.getKeyModifiersText(KeyEvent.ALT_MASK));
      myShift = new ModifierBox("shift", KeyEvent.getKeyModifiersText(KeyEvent.SHIFT_MASK));
      myMeta = new ModifierBox("meta", KeyEvent.getKeyModifiersText(KeyEvent.META_MASK));

      modifiers.add(new JLabel("Modifiers:"));
      modifiers.add(myCtrl);
      modifiers.add(myAlt);
      modifiers.add(myShift);
      if (SystemInfo.isMac) {
        modifiers.add(myMeta);
      }

      JPanel hold = new JPanel(new FlowLayout(FlowLayout.CENTER));
      hold.add(new JLabel("Hold time:"));
      myHoldTime = new JFormattedTextField(NumberFormat.getIntegerInstance());
      myHoldTime.setColumns(5);
      myHoldTime.setHorizontalAlignment(JTextField.RIGHT);
      myHoldTime.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          String txt = myHoldTime.getText();
          if (txt != null) {
            try {
              Integer value = Integer.valueOf(txt);
              myDelay = value.intValue();
            }
            catch (NumberFormatException e1) {
            }
          }
        }
      });
      hold.add(myHoldTime);
      hold.add(new JLabel("ms"));

      kbConfig.add(modifiers);
      kbConfig.add(hold);

      kbConfig.setBorder(new TitledBorder("Keyboard Configuration"));

      myBox.add(kbConfig);

      myConflicts = new JPanel();

      myBox.add(myConflicts);

      updateConflicts();
    }

    public void reset(boolean isEnabled, Set<String> modifiers, int delay) {
      myQaEnabled = isEnabled;
      myModifiers.clear();
      myModifiers.addAll(modifiers);
      myDelay = delay;

      myEnabled.setSelected(myQaEnabled);
      myCtrl.readMask();
      myAlt.readMask();
      myShift.readMask();
      myMeta.readMask();

      myHoldTime.setText(Integer.valueOf(delay).toString());

      processEnabled();

      updateConflicts();
    }

    private void updateConflicts() {
      myConflicts.removeAll();
      myConflicts.setBorder(null);

      if (!myQaEnabled) {
        return;
      }

      if (myModifiers.size() == 0) {
        myConflicts.setLayout(new BorderLayout());
        myConflicts.add(getSmallLabel("Without assigning modifier keys Quick Access will not function"), BorderLayout.NORTH);
        return;
      }

      myConflicts.setLayout(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();
      c.insets = new Insets(0, 4, 0, 4);

      boolean hasConflicts = printConflict(c, KeyEvent.VK_UP, SWITCH_UP);
      hasConflicts |= printConflict(c, KeyEvent.VK_DOWN, SWITCH_DOWN);
      hasConflicts |= printConflict(c, KeyEvent.VK_LEFT, SWITCH_LEFT);
      hasConflicts |= printConflict(c, KeyEvent.VK_RIGHT, SWITCH_RIGHT);
      hasConflicts |= printConflict(c, KeyEvent.VK_ENTER, SWITCH_APPLY);

      if (hasConflicts) {
        myConflicts.setBorder(new TitledBorder("Conflicts"));
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        myConflicts.add(new SeparatorWithText(), c);

        c.gridx = 0;
        c.gridy++;
        myConflicts.add(getSmallLabel("These conflicting actions may be not what you use a lot"), c);
      }
    }

    private JLabel getSmallLabel(final String text) {
      JLabel message = new JLabel(text, null, JLabel.CENTER);
      message.setFont(message.getFont().deriveFont(message.getFont().getStyle(), message.getFont().getSize() - 2));
      return message;
    }

    private boolean printConflict(GridBagConstraints c, int actionKey, String actionId) {
      boolean hasConflicts = false;

      int mask = getModiferMask(myModifiers);

      KeyboardShortcut sc = new KeyboardShortcut(KeyStroke.getKeyStroke(actionKey, mask), null);

      Map<String,ArrayList<KeyboardShortcut>> conflictMap = myKeymap.getConflicts(actionId, sc);
      if (conflictMap.size() > 0) {
        hasConflicts = true;

        JLabel scText = new JLabel(sc.toString());
        c.gridy++;
        c.gridx = 0;
        myConflicts.add(scText, c);

        Iterator<String> actions = conflictMap.keySet().iterator();
        while (actions.hasNext()) {
          String each = actions.next();
          AnAction eachAnAction = ActionManager.getInstance().getAction(each);
          if (eachAnAction != null) {
            String text = eachAnAction.getTemplatePresentation().getText();
            JLabel eachAction = new JLabel(text != null && text.length() > 0 ? text : each);
            c.gridx = 1;
            myConflicts.add(eachAction, c);
            c.gridy++;
          }
        }
      }

      c.gridx = 0;
      c.gridwidth = 2;
      c.gridy++;

      myConflicts.add(new SeparatorWithText(), c);
      c.gridwidth = 1;

      return hasConflicts;

    }

    private void processEnabled() {
      new AwtVisitor(this) {
        @Override
        public boolean visit(Component component) {
          if (component != myEnabled) {
            component.setEnabled(myQaEnabled);
          }
          return false;
        }
      };
    }

    public boolean isQaEnabled() {
      return myEnabled.isSelected();
    }

    public Set<String> getModifiers() {
      return myModifiers;
    }

    public int getHoldTime() {
      return myDelay;
    }

    private class ModifierBox extends JCheckBox {

      private String myModifierText;

      private ModifierBox(String modifierText, String text) {
        setText(text);
        myModifierText = modifierText;
        addItemListener(new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            applyMask();
            updateConflicts();
          }
        });
      }

      private void applyMask() {
        if (isSelected()) {
          myModifiers.add(myModifierText);
        }
        else {
          myModifiers.remove(myModifierText);
        }
      }

      public boolean readMask() {
        boolean selected = myModifiers.contains(myModifierText);
        setSelected(selected);
        return selected; 
      }
    }
  }

  public static void main(String[] args) {
    JFrame frame = new JFrame();
    QuickAccessSettings cfg = new QuickAccessSettings();
    JComponent c = cfg.createComponent();

    frame.add(c);
    frame.setBounds(100, 100, 400, 400);
    frame.show();
  }
}
