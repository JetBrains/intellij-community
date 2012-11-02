package com.intellij.execution.util;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author traff
 */
public final class PathMappingsComponent extends LabeledComponent<TextFieldWithBrowseButton> implements UserActivityProviderComponent {

  private final ArrayList<ChangeListener> myListeners = new ArrayList<ChangeListener>(2);

  @NotNull
  private PathMappingSettings myMappingSettings = new PathMappingSettings();

  public PathMappingsComponent() {
    super();
    final TextFieldWithBrowseButton pathTextField = new TextFieldWithBrowseButton();
    pathTextField.setEditable(false);
    setComponent(pathTextField);
    setText("Path mappings:");
    getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        showConfigureMappingsDialog();
      }
    });
  }

  private void showConfigureMappingsDialog() {
    new MyPathMappingsDialog(this).show();
  }

  @NotNull
  public PathMappingSettings getMappingSettings() {
    return myMappingSettings;
  }

  public void setMappingSettings(@Nullable final PathMappingSettings mappingSettings) {
    if (mappingSettings == null) {
      myMappingSettings = new PathMappingSettings();
    }
    else {
      myMappingSettings = mappingSettings;
    }

    setTextRepresentation(myMappingSettings);

    fireStateChanged();
  }

  private void setTextRepresentation(@NotNull PathMappingSettings mappingSettings) {
    final StringBuilder sb = new StringBuilder();
    for (PathMappingSettings.PathMapping mapping : mappingSettings.getPathMappings()) {
      sb.append(mapping.getLocalRoot()).append("=").append(mapping.getRemoteRoot()).append(";");
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1); //trim last ;
    }
    getComponent().setText(sb.toString());
  }

  public void addChangeListener(final ChangeListener changeListener) {
    myListeners.add(changeListener);
  }

  public void removeChangeListener(final ChangeListener changeListener) {
    myListeners.remove(changeListener);
  }

  private void fireStateChanged() {
    for (ChangeListener listener : myListeners) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  private static class MyPathMappingsDialog extends DialogWrapper {
    private final PathMappingTable myPathMappingTable;

    private final JPanel myWholePanel = new JPanel(new BorderLayout());
    private PathMappingsComponent myMappingsComponent;

    protected MyPathMappingsDialog(PathMappingsComponent mappingsComponent) {
      super(mappingsComponent, true);
      myMappingsComponent = mappingsComponent;
      myPathMappingTable = new PathMappingTable();

      myPathMappingTable.setValues(mappingsComponent.getMappingSettings().getPathMappings());
      myWholePanel.add(myPathMappingTable.getComponent(), BorderLayout.CENTER);
      setTitle("Edit Path Mappings");
      init();
    }

    @Nullable
    protected JComponent createCenterPanel() {
      return myWholePanel;
    }

    protected void doOKAction() {
      myPathMappingTable.stopEditing();

      myMappingsComponent.setMappingSettings(myPathMappingTable.getPathMappingSettings());

      super.doOKAction();
    }
  }
}
