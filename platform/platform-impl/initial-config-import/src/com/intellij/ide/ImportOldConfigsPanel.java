// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.ide.actions.ImportSettingsFilenameFilter;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.openapi.vfs.local.CoreLocalVirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipFile;

public final class ImportOldConfigsPanel extends JDialog {
  private JPanel myRootPanel;
  private JRadioButton myRbImportAuto;
  private JRadioButton myRbImport;
  private TextFieldWithBrowseButton myPrevInstallation;
  private JRadioButton myRbDoNotImport;
  private JButton myOkButton;
  private ComboBox<Path> myComboBoxOldPaths;

  private final List<Path> myGuessedOldConfigDirs;
  private final Function<Path, Pair<Path, Path>> myValidator;
  private final String myProductName;
  private Path myLastSelection = null;
  private Pair<Path, Path> myResult;

  public ImportOldConfigsPanel(@NotNull List<Path> guessedOldConfigDirs, @NotNull Function<Path, Pair<Path, Path>> validator) {
    super((Dialog)null, true);

    ComponentUtil.decorateWindowHeader(rootPane);

    myGuessedOldConfigDirs = guessedOldConfigDirs;
    myValidator = validator;
    myProductName = ApplicationNamesInfo.getInstance().getFullProductName();
    setTitle(BootstrapBundle.message("import.settings.title", myProductName));
    init();
  }

  private void init() {
    MnemonicHelper.init(getContentPane());

    var group = new ButtonGroup();
    group.add(myRbImportAuto);
    group.add(myRbImport);
    group.add(myRbDoNotImport);
    myRbDoNotImport.setSelected(true);

    if (myGuessedOldConfigDirs.isEmpty()) {
      myRbImportAuto.setVisible(false);
      myComboBoxOldPaths.setVisible(false);
    }
    else {
      myComboBoxOldPaths.setModel(new CollectionComboBoxModel<>(myGuessedOldConfigDirs));
      myComboBoxOldPaths.setSelectedItem(myGuessedOldConfigDirs.getFirst());
      myRbImportAuto.setSelected(true);
    }
    for (var e = group.getElements(); e.hasMoreElements(); ) {
      e.nextElement().addChangeListener(event -> update());
    }
    if (OS.CURRENT == OS.macOS) {
      myLastSelection = Path.of("/Applications");
    }
    else if (OS.CURRENT == OS.Windows) {
      var programFiles = System.getenv("ProgramFiles");
      if (programFiles != null) {
        var candidate = Path.of(programFiles, "JetBrains");
        myLastSelection = Files.isDirectory(candidate) ? candidate : Path.of(programFiles);
      }
    }
    myPrevInstallation.setTextFieldPreferredWidth(50);
    myPrevInstallation.addActionListener(e -> {
      var chooserDescriptor = FileChooserDescriptorFactory.singleFile().withHideIgnored(false);
      ConfigImportHelper.setSettingsFilter(chooserDescriptor);
      var fileRef = Ref.<Path>create();
      var chooser = FileChooserFactoryImpl.createNativePathChooserIfEnabled(chooserDescriptor, null, myRootPanel);
      if (chooser != null) {
        var vf = myLastSelection != null ? new CoreLocalVirtualFile(new CoreLocalFileSystem(), myLastSelection) : null;
        chooser.choose(vf, files -> fileRef.set(Path.of(files.getFirst().getPresentableUrl())));
      }
      else {
        @SuppressWarnings("IO_FILE_USAGE") var directory = myLastSelection != null ? myLastSelection.getParent().toFile() : null;
        @SuppressWarnings("IO_FILE_USAGE") var selectedFile = myLastSelection != null ? myLastSelection.toFile() : null;
        var fc = new JFileChooser();
        fc.setCurrentDirectory(directory);
        fc.setSelectedFile(selectedFile);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileHidingEnabled(OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS);
        fc.setFileFilter(new FileNameExtensionFilter(BootstrapBundle.message("import.settings.filter"), "zip", "jar"));
        var returnVal = fc.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          var file = fc.getSelectedFile();
          if (file != null) {
            fileRef.set(file.toPath());
            myPrevInstallation.setText(file.getAbsolutePath());
          }
        }
      }
      if (!fileRef.isNull()) {
        myLastSelection = fileRef.get();
        myPrevInstallation.setText(fileRef.get().toString());
      }
    });

    myOkButton.addActionListener(e -> close());

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myRootPanel);
    getRootPane().setDefaultButton(myOkButton);

    update();
    pack();
    setLocationRelativeTo(null);
    setMinimumSize(getSize());
  }

  private void update() {
    myComboBoxOldPaths.setEnabled(myRbImportAuto.isSelected());
    myPrevInstallation.setEnabled(myRbImport.isSelected());
  }

  private void close() {
    if (myRbImport.isSelected()) {
      var text = myPrevInstallation.getText().trim();
      if (text.isEmpty()) {
        showError(BootstrapBundle.message("import.chooser.error.empty", myProductName));
        return;
      }

      var selectedDir = Path.of(text).toAbsolutePath().normalize();

      if (Files.isRegularFile(selectedDir)) {
        if (!isValidSettingsFile(selectedDir)) {
          showError(BootstrapBundle.message("import.chooser.error.invalid", selectedDir));
          return;
        }
        myResult = new Pair<>(selectedDir, null);
      }
      else {
        if (selectedDir.equals(PathManager.getHomeDir()) || selectedDir.equals(PathManager.getConfigDir())) {
          showError(BootstrapBundle.message("import.chooser.error.current", myProductName));
          return;
        }

        var result = myValidator.apply(selectedDir);
        if (result == null) {
          showError(BootstrapBundle.message("import.chooser.error.unrecognized", selectedDir, myProductName));
          return;
        }

        myResult = result;
      }
    }

    dispose();
  }

  private void showError(@NlsContexts.DialogMessage String message) {
    var title = BootstrapBundle.message("import.chooser.error.title");
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
  }

  private static boolean isValidSettingsFile(Path file) {
    try (@SuppressWarnings("IO_FILE_USAGE") var zip = new ZipFile(file.toFile())) {
      return zip.getEntry(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER) != null;
    }
    catch (IOException ignored) {
      return false;
    }
  }

  public @Nullable Pair<Path, Path> getSelectedFile() {
    ImportOldConfigsUsagesCollector.INSTANCE.saveImportOldConfigType(myRbImportAuto, myRbImport, myRbDoNotImport, myResult != null);

    if (myRbImportAuto.isSelected()) {
      return new Pair<>(myGuessedOldConfigDirs.get(Math.max(myComboBoxOldPaths.getSelectedIndex(), 0)), null);
    }
    if (myRbImport.isSelected()) {
      return myResult;
    }
    return null;
  }
}
