/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.options.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.DocumentAdapter;
import org.intellij.images.ImagesBundle;
import org.intellij.images.options.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Options UI form bean.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImagesOptionsComponent {
  private JPanel contentPane;
  private JCheckBox showGrid;
  private JLabel gridLineZoomFactorLabel;
  private JSpinner gridLineZoomFactor;
  private JLabel gridLineSpanLabel;
  private JSpinner gridLineSpan;
  private JCheckBox showChessboard;
  private JSpinner chessboardSize;
  private JLabel chessboardSizeLabel;
  private JLabel externalEditorLabel;
  private TextFieldWithBrowseButton externalEditorPath;

  // Options
  private final Options options = new OptionsImpl();

  ImagesOptionsComponent() {
    // Setup labels
    gridLineZoomFactorLabel.setLabelFor(gridLineZoomFactor);
    gridLineSpanLabel.setLabelFor(gridLineSpan);
    chessboardSizeLabel.setLabelFor(chessboardSize);
    externalEditorLabel.setLabelFor(externalEditorPath);

    // Setup spinners models
    gridLineZoomFactor.setModel(new SpinnerNumberModel(GridOptions.DEFAULT_LINE_ZOOM_FACTOR, 2, 8, 1));
    gridLineSpan.setModel(new SpinnerNumberModel(GridOptions.DEFAULT_LINE_SPAN, 1, 100, 1));
    chessboardSize.setModel(new SpinnerNumberModel(TransparencyChessboardOptions.DEFAULT_CELL_SIZE, 1, 100, 1));

    // Setup listeners for chnages
    showGrid.addItemListener(new CheckboxOptionsListener(GridOptions.ATTR_SHOW_DEFAULT));
    gridLineZoomFactor.addChangeListener(new SpinnerOptionsListener(GridOptions.ATTR_LINE_ZOOM_FACTOR));
    gridLineSpan.addChangeListener(new SpinnerOptionsListener(GridOptions.ATTR_LINE_SPAN));
    showChessboard.addItemListener(new CheckboxOptionsListener(TransparencyChessboardOptions.ATTR_SHOW_DEFAULT));
    chessboardSize.addChangeListener(new SpinnerOptionsListener(TransparencyChessboardOptions.ATTR_CELL_SIZE));
    externalEditorPath.getTextField().getDocument()
      .addDocumentListener(new TextDocumentOptionsListener(ExternalEditorOptions.ATTR_EXECUTABLE_PATH));

    externalEditorPath.addActionListener(new ExternalEditorPathActionListener());

    updateUI();
  }

  public JPanel getContentPane() {
    return contentPane;
  }

  private static class LinkEnabledListener implements ItemListener {
    private final JComponent[] children;

    LinkEnabledListener(JComponent[] children) {
      this.children = children.clone();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
      setSelected(e.getStateChange() == ItemEvent.SELECTED);
    }

    private void setSelected(boolean selected) {
      for (JComponent component : children) {
        component.setEnabled(selected);
      }
    }
  }

  public @NotNull Options getOptions() {
    return options;
  }

  public void updateUI() {
    // Grid options
    EditorOptions editorOptions = options.getEditorOptions();
    ExternalEditorOptions externalEditorOptions = options.getExternalEditorOptions();

    GridOptions gridOptions = editorOptions.getGridOptions();
    showGrid.setSelected(gridOptions.isShowDefault());
    gridLineZoomFactor.setValue(gridOptions.getLineZoomFactor());
    gridLineSpan.setValue(gridOptions.getLineSpan());
    TransparencyChessboardOptions transparencyChessboardOptions = editorOptions.getTransparencyChessboardOptions();
    showChessboard.setSelected(transparencyChessboardOptions.isShowDefault());
    chessboardSize.setValue(transparencyChessboardOptions.getCellSize());
    externalEditorPath.setText(externalEditorOptions.getExecutablePath());
  }

  private final class CheckboxOptionsListener implements ItemListener {
    private final @NotNull String name;

    private CheckboxOptionsListener(@NotNull String name) {
      this.name = name;
    }

    @Override
    @SuppressWarnings({"UnnecessaryBoxing"})
    public void itemStateChanged(ItemEvent e) {
      options.setOption(name, Boolean.valueOf(ItemEvent.SELECTED == e.getStateChange()));
    }
  }

  private final class SpinnerOptionsListener implements ChangeListener {
    private final String name;

    private SpinnerOptionsListener(String name) {
      this.name = name;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      JSpinner source = (JSpinner)e.getSource();
      options.setOption(name, source.getValue());
    }
  }

  private final class ColorOptionsListener implements ActionListener {
    private final String name;

    private ColorOptionsListener(String name) {
      this.name = name;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ColorPanel source = (ColorPanel)e.getSource();
      options.setOption(name, source.getSelectedColor());
    }
  }

  private final class TextDocumentOptionsListener extends DocumentAdapter {
    private final String name;

    TextDocumentOptionsListener(String name) {
      this.name = name;
    }

    @Override
    protected void textChanged(@NotNull DocumentEvent documentEvent) {
      Document document = documentEvent.getDocument();
      Position startPosition = document.getStartPosition();
      try {
        options.setOption(name, document.getText(startPosition.getOffset(), document.getLength()));
      }
      catch (BadLocationException e) {
        // Ignore
      }
    }
  }

  private final class ExternalEditorPathActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      Application application = ApplicationManager.getApplication();
      VirtualFile previous = application.runWriteAction((NullableComputable<VirtualFile>)() -> {
        final String path = FileUtil.toSystemIndependentName(externalEditorPath.getText());
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      });
      FileChooserDescriptor fileDescriptor = new FileChooserDescriptor(true, SystemInfo.isMac, false, false, false, false);
      fileDescriptor.setShowFileSystemRoots(true);
      fileDescriptor.setTitle(ImagesBundle.message("select.external.executable.title"));
      fileDescriptor.setDescription(ImagesBundle.message("select.external.executable.message"));
      FileChooser.chooseFiles(fileDescriptor, null, previous, files -> {
        String path = files.get(0).getPath();
        externalEditorPath.setText(path);
      });
    }
  }
}
