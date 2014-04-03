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
package com.intellij.ide.startupWizardV2;

import com.intellij.CommonBundle;
import com.intellij.ide.ui.laf.IntelliJLookAndFeelInfo;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class LafPage extends AbstractWizardPage {
  private static final String AQUA = "Default";
  private static final String DARCULA = "Darcula";
  private static final String ALLOY = "Alloy. IDEA Theme";
  private static final String INTELLIJ = "IntelliJ";
  private static final String GTK = "GTK+";

  private Map<UIManager.LookAndFeelInfo, Icon> myLafs = new LinkedHashMap<UIManager.LookAndFeelInfo, Icon>();
  private final JLabel myPreviewLabel = new JLabel();
  private final boolean myVertical;

  public LafPage() {
    boolean isAlloyAvailable = false;
    boolean isGTKAvailable = false;
    for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
      if (ALLOY.equals(info.getName())) {
        isAlloyAvailable = true;
      }
      if (GTK.equals(info.getName())) {
        isGTKAvailable = true;
      }
    }
    final String osPrefix = SystemInfo.isMac ? "OSX" : SystemInfo.isWindows ? "Windows" : "Linux";

    if (SystemInfo.isMac) {
      myLafs.put(new UIManager.LookAndFeelInfo(AQUA, "com.apple.laf.AquaLookAndFeel"), IconLoader.getIcon("/lafs/OSXAqua.png"));
    }
    else {
      if (isAlloyAvailable) {
        myLafs.put(new UIManager.LookAndFeelInfo(ALLOY, "com.incors.plaf.alloy.AlloyIdea"),
                   IconLoader.getIcon("/lafs/" + osPrefix + "Alloy.png"));
      }
      else {
        myLafs.put(new IntelliJLookAndFeelInfo(), IconLoader.getIcon("/lafs/" + osPrefix + "IntelliJ.png"));
      }
    }

    myLafs.put(new DarculaLookAndFeelInfo(), IconLoader.getIcon("/lafs/" + osPrefix + "Darcula.png"));

    if (SystemInfo.isLinux && isGTKAvailable) {
      myLafs.put(new UIManager.LookAndFeelInfo(GTK, "com.sun.java.swing.plaf.gtk.GTKLookAndFeel"),
                 IconLoader.getIcon("/lafs/" + osPrefix + "GTK.png"));
    }

    myVertical = myLafs.size() > 2;
    JPanel buttonPanel = new JPanel(myVertical ? new GridLayout(myLafs.size(), 1, 5, 5) : new GridLayout(1, myLafs.size(), 5, 5));
    ButtonGroup group = new ButtonGroup();
    boolean isFirst = true;
    for (Map.Entry<UIManager.LookAndFeelInfo, Icon> entry : myLafs.entrySet()) {
      JPanel p = new JPanel(new BorderLayout());
      JRadioButton radioButton = new JRadioButton(entry.getKey().getName(), isFirst);
      isFirst = false;
      group.add(radioButton);
      p.add(radioButton, BorderLayout.NORTH);
      Icon icon = entry.getValue();
      buttonPanel.add(new JLabel(myVertical ?
                     new ImageIcon(IconUtil.toImage(icon)
                                     .getScaledInstance(icon.getIconWidth() / 3, icon.getIconHeight() / 3, Image.SCALE_AREA_AVERAGING))
                              : icon));
    }
    setLayout(new BorderLayout());
    add(buttonPanel, BorderLayout.CENTER);
    if (myVertical) {
      add(myPreviewLabel, BorderLayout.EAST);
    }
  }

  @NotNull
  @Override
  String getTitle() {
    return "Set UI Theme";
  }

  @NotNull
  @Override
  String getID() {
    return "UI Themes";
  }

  @NotNull
  @Override
  String getFooter() {
    return "UI theme can be changed later in " + CommonBundle.settingsTitle() + " | Appearance.";
  }
}
