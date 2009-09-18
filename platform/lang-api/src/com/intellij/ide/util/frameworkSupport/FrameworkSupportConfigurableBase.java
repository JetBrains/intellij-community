package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
*/
public class FrameworkSupportConfigurableBase extends FrameworkSupportConfigurable {
  private JComboBox myVersionComboBox;
  private final FrameworkSupportProviderBase myFrameworkSupportProvider;
  private JPanel myMainPanel;
  private JLabel myDescriptionLabel;

  public FrameworkSupportConfigurableBase(FrameworkSupportProviderBase frameworkSupportProvider) {
    this(frameworkSupportProvider, Collections.<FrameworkVersion>emptyList(), null);
  }

  public FrameworkSupportConfigurableBase(final FrameworkSupportProviderBase frameworkSupportProvider, List<FrameworkVersion> versions, String versionLabelText) {
    myFrameworkSupportProvider = frameworkSupportProvider;
    if (versions.size() > 0) {
      myDescriptionLabel.setText(versionLabelText);
      String maxValue = "";
      FrameworkVersion defaultVersion = versions.get(versions.size() - 1);
      for (FrameworkVersion version : versions) {
        myVersionComboBox.addItem(version);
        FontMetrics fontMetrics = myVersionComboBox.getFontMetrics(myVersionComboBox.getFont());
        if (fontMetrics.stringWidth(version.getVersionName()) > fontMetrics.stringWidth(maxValue)) {
          maxValue = version.getVersionName();
        }
        if (version.isDefault()) {
          defaultVersion = version;
        }
      }
      myVersionComboBox.setPrototypeDisplayValue(maxValue + "_");
      myVersionComboBox.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (value instanceof FrameworkVersion) {
            setText(((FrameworkVersion)value).getVersionName());
          }
          return component;
        }
      });
      myVersionComboBox.setSelectedItem(defaultVersion);
      myVersionComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          fireFrameworkVersionChanged();
        }
      });
    }
    else {
      myDescriptionLabel.setVisible(false);
      myVersionComboBox.setVisible(false);
    }
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  @NotNull
  public LibraryInfo[] getLibraries() {
    return getSelectedVersion().getLibraries();
  }

  public void addSupport(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel, final @Nullable Library library) {
    myFrameworkSupportProvider.addSupport(module, rootModel, getSelectedVersion(), library);
  }

  public FrameworkVersion getSelectedVersion() {
    return (FrameworkVersion)myVersionComboBox.getSelectedItem();
  }

}
