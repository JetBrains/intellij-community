/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.customize;

import com.intellij.CommonBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class CustomizeUIThemeStepPanel extends AbstractCustomizeWizardStep {

  private static final String DEFAULT = "Default";
  private static final String DARCULA = "Darcula";
  private static final String INTELLIJ = "IntelliJ";
  private static final String ALLOY = "Alloy. IDEA Theme";
  private static final String GTK = "GTK+";
  private boolean myInitial = true;
  private boolean myColumnMode;
  private JLabel myPreviewLabel;
  private Map<String, Icon> myLafNames = new LinkedHashMap<String, Icon>();

  public CustomizeUIThemeStepPanel() {
    setLayout(new BorderLayout(10, 10));
    IconLoader.activate();

    if (SystemInfo.isMac) {
      myLafNames.put(DEFAULT, IconLoader.getIcon("/lafs/OSXAqua.png"));
      myLafNames.put(DARCULA, IconLoader.getIcon("/lafs/OSXDarcula.png"));
    }
    else if (SystemInfo.isWindows) {
      if (PlatformUtils.isIdeaCommunity()) {
        myLafNames.put(INTELLIJ, IconLoader.getIcon("/lafs/WindowsIntelliJ.png"));
      }
      else {
        myLafNames.put(ALLOY, IconLoader.getIcon("/lafs/WindowsAlloy.png"));
      }
      myLafNames.put(DARCULA, IconLoader.getIcon("/lafs/WindowsDarcula.png"));
    }
    else {
      myLafNames.put(INTELLIJ, IconLoader.getIcon("/lafs/LinuxIntelliJ.png"));
      myLafNames.put(DARCULA, IconLoader.getIcon("/lafs/LinuxDarcula.png"));
      myLafNames.put(GTK, IconLoader.getIcon("/lafs/LinuxGTK.png"));
    }
    myColumnMode = myLafNames.size() > 2;
    JPanel buttonsPanel = new JPanel(new GridLayout(myColumnMode ? myLafNames.size() : 1, myColumnMode ? 1 : myLafNames.size(), 5, 5));
    ButtonGroup group = new ButtonGroup();
    String myDefaultLafName = null;

    for (Map.Entry<String, Icon> entry : myLafNames.entrySet()) {
      final String lafName = entry.getKey();
      Icon icon = entry.getValue();
      final JRadioButton radioButton = new JRadioButton(lafName, myDefaultLafName == null);
      radioButton.setOpaque(false);
      if (myDefaultLafName == null) {
        radioButton.setSelected(true);
        myDefaultLafName = lafName;
      }
      final JPanel panel = createBigButtonPanel(new BorderLayout(10, 10), radioButton, new Runnable() {
        @Override
        public void run() {
          applyLaf(lafName, CustomizeUIThemeStepPanel.this);
        }
      });
      panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      panel.add(radioButton, myColumnMode ? BorderLayout.WEST : BorderLayout.NORTH);
      final JLabel label = new JLabel(myColumnMode ? IconUtil.scale(icon, .33) : icon);
      label.setVerticalAlignment(SwingConstants.TOP);
      label.setHorizontalAlignment(SwingConstants.RIGHT);
      panel.add(label, BorderLayout.CENTER);

      group.add(radioButton);
      buttonsPanel.add(panel);
    }
    add(buttonsPanel, BorderLayout.CENTER);
    myPreviewLabel = new JLabel();
    myPreviewLabel.setHorizontalAlignment(myColumnMode ? SwingConstants.LEFT : SwingConstants.CENTER);
    myPreviewLabel.setVerticalAlignment(SwingConstants.CENTER);
    if (myColumnMode) {
      add(buttonsPanel, BorderLayout.WEST);
      JPanel wrapperPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      wrapperPanel.add(myPreviewLabel);
      add(wrapperPanel, BorderLayout.CENTER);
    }
    applyLaf(myDefaultLafName, this);
    myInitial = false;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.width += 30;
    return size;
  }


  @Override
  public String getTitle() {
    return "UI Themes";
  }

  @Override
  public String getHTMLHeader() {
    return "<html><body><h2>Set UI theme</h2>&nbsp;</body></html>";
  }

  @Override
  public String getHTMLFooter() {
    return "UI theme can be changed later in " + CommonBundle.settingsTitle() + " | Appearance";
  }

  private void applyLaf(String lafName, Component component) {
    UIManager.LookAndFeelInfo info = getLookAndFeelInfo(lafName);
    if (info == null) return;
    try {
      UIManager.setLookAndFeel(info.getClassName());
      String className = info.getClassName();
      if (lafName == DARCULA) {
        className = DarculaLookAndFeelInfo.CLASS_NAME;
      }
      if (!myInitial) {
        StartupUtil.setWizardLAF(className);
      }
      Window window = SwingUtilities.getWindowAncestor(component);
      if (window != null) {
        window.setBackground(new Color(UIUtil.getPanelBackground().getRGB()));
        SwingUtilities.updateComponentTreeUI(window);
      }
      if (ApplicationManager.getApplication() != null) {
        LafManager.getInstance().setCurrentLookAndFeel(info);
      }
      if (myColumnMode) {
        myPreviewLabel.setIcon(myLafNames.get(lafName));
        myPreviewLabel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Label.disabledForeground")));
      }
    }
    catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    catch (InstantiationException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    catch (UnsupportedLookAndFeelException e) {
      e.printStackTrace();
    }
  }

  @Nullable
  private static UIManager.LookAndFeelInfo getLookAndFeelInfo(String name) {
    if (DEFAULT.equals(name)) return new UIManager.LookAndFeelInfo(DEFAULT, "com.apple.laf.AquaLookAndFeel");
    if (DARCULA.equals(name)) return new UIManager.LookAndFeelInfo(DARCULA, DarculaLaf.class.getName());
    if (INTELLIJ.equals(name)) return new UIManager.LookAndFeelInfo(INTELLIJ, IntelliJLaf.class.getName());
    if (ALLOY.equals(name)) return new UIManager.LookAndFeelInfo(ALLOY, "com.incors.plaf.alloy.AlloyIdea");
    if (GTK.equals(name)) return new UIManager.LookAndFeelInfo(GTK, "com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
    return null;
  }
}
