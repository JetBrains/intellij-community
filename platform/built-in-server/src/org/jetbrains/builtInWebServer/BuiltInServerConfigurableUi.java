package org.jetbrains.builtInWebServer;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.ui.PortField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class BuiltInServerConfigurableUi implements ConfigurableUi<BuiltInServerOptions> {
  private JPanel mainPanel;

  private PortField builtInServerPort;
  private JCheckBox builtInServerAvailableExternallyCheckBox;
  private JCheckBox allowUnsignedRequestsCheckBox;
  private JLabel portLabel;

  public BuiltInServerConfigurableUi() {
    portLabel.setLabelFor(builtInServerPort);
    builtInServerPort.setMin(1024);
    builtInServerPort.addChangeListener(e -> {
      boolean isEnabled = builtInServerPort.getNumber() < BuiltInServerOptions.DEFAULT_PORT;
      builtInServerAvailableExternallyCheckBox.setEnabled(isEnabled);
      builtInServerAvailableExternallyCheckBox.setToolTipText(isEnabled ? null : "Can’t be enabled for default port (port number >= 63342). Please change it.");
    });
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }

  @Override
  public boolean isModified(@NotNull BuiltInServerOptions settings) {
    return builtInServerPort.getNumber() != settings.builtInServerPort ||
           builtInServerAvailableExternallyCheckBox.isSelected() != settings.builtInServerAvailableExternally ||
           allowUnsignedRequestsCheckBox.isSelected() != settings.allowUnsignedRequests;
  }

  @Override
  public void apply(@NotNull BuiltInServerOptions settings) {
    boolean builtInServerPortChanged = settings.builtInServerPort != builtInServerPort.getNumber() || settings.builtInServerAvailableExternally != builtInServerAvailableExternallyCheckBox.isSelected();
    settings.allowUnsignedRequests = allowUnsignedRequestsCheckBox.isSelected();
    if (builtInServerPortChanged) {
      settings.builtInServerPort = builtInServerPort.getNumber();
      settings.builtInServerAvailableExternally = builtInServerAvailableExternallyCheckBox.isSelected();

      BuiltInServerOptions.onBuiltInServerPortChanged();
    }
  }

  @Override
  public void reset(@NotNull BuiltInServerOptions settings) {
    builtInServerPort.setNumber(settings.builtInServerPort);
    builtInServerAvailableExternallyCheckBox.setSelected(settings.builtInServerAvailableExternally);
    allowUnsignedRequestsCheckBox.setSelected(settings.allowUnsignedRequests);
  }
}
