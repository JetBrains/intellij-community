/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 6, 2004
 */
public class SourcePathsStep extends AbstractStepWithProgress<List<JavaModuleSourceRoot>> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.newProjectWizard.SourcePathsStep");

  private String myCurrentMode;
  @NonNls private static final String CREATE_SOURCE_PANEL = "create_source";
  @NonNls private static final String CHOOSE_SOURCE_PANEL = "choose_source";

  private final SourcePathsBuilder myBuilder;
  private final Icon myIcon;
  private final String myHelpId;
  private ElementsChooser<JavaModuleSourceRoot> mySourcePathsChooser;
  private String myCurrentContentEntryPath = null;
  private JRadioButton myRbCreateSource;
  private JRadioButton myRbNoSource;
  private JTextField myTfSourceDirectoryName;
  private JTextField myTfFullPath;
  private JPanel myResultPanel;

  public SourcePathsStep(SourcePathsBuilder builder, Icon icon, @NonNls String helpId) {
    super(IdeBundle.message("prompt.stop.searching.for.sources", ApplicationNamesInfo.getInstance().getProductName()));
    myBuilder = builder;
    myIcon = icon;
    myHelpId = helpId;
  }

  protected JComponent createResultsPanel() {
    myResultPanel = new JPanel(new CardLayout());
    myResultPanel.add(createComponentForEmptyRootCase(), CREATE_SOURCE_PANEL);
    myResultPanel.add(createComponentForChooseSources(), CHOOSE_SOURCE_PANEL);
    return myResultPanel;
  }

  private JComponent createComponentForEmptyRootCase() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final String text = IdeBundle.message("prompt.please.specify.java.sources.directory");

    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                            JBUI.insets(8, 10, 0, 10), 0, 0));

    myRbCreateSource = new JRadioButton(IdeBundle.message("radio.create.source.directory"), true);
    panel.add(myRbCreateSource, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                       JBUI.insets(8, 10, 0, 10), 0, 0));

    myTfSourceDirectoryName = new JTextField(suggestSourceDirectoryName());
    final JLabel srcPathLabel = new JLabel(IdeBundle.message("prompt.enter.relative.path.to.module.content.root", File.separator));
    panel.add(srcPathLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                   JBUI.insets(8, 30, 0, 0), 0, 0));
    final FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    chooserDescriptor.withTreeRootVisible(true);
    final FieldPanel fieldPanel = createFieldPanel(myTfSourceDirectoryName, null, new BrowsePathListener(myTfSourceDirectoryName, chooserDescriptor));
    panel.add(fieldPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                 JBUI.insets(8, 30, 0, 10), 0, 0));

    myRbNoSource = new JRadioButton(IdeBundle.message("radio.do.not.create.source.directory"), true);
    panel.add(myRbNoSource, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                   JBUI.insets(8, 10, 0, 10), 0, 0));

    final JLabel fullPathLabel = new JLabel(IdeBundle.message("label.source.directory"));
    panel.add(fullPathLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE,
                                                    JBUI.insets(8, 10, 0, 10), 0, 0));

    myTfFullPath = new JTextField();
    myTfFullPath.setEditable(false);
    myTfFullPath.setOpaque(false);
    final Insets borderInsets = myTfFullPath.getBorder().getBorderInsets(myTfFullPath);
    myTfFullPath.setBorder(BorderFactory.createEmptyBorder(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right));
    panel.add(myTfFullPath, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.HORIZONTAL,
                                                   JBUI.insets(8, 10), 0, 0));

    ButtonGroup group = new ButtonGroup();
    group.add(myRbCreateSource);
    group.add(myRbNoSource);
    myTfSourceDirectoryName.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateFullPathField();
      }
    });

    myRbCreateSource.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        final boolean enabled = e.getStateChange() == ItemEvent.SELECTED;
        srcPathLabel.setEnabled(enabled);
        fieldPanel.setEnabled(enabled);
        fullPathLabel.setVisible(enabled);
        myTfFullPath.setVisible(enabled);
        if (enabled) {
          myTfSourceDirectoryName.requestFocus();
        }
      }
    });
    return panel;
  }

  @NonNls protected String suggestSourceDirectoryName() {
    return "src";
  }

  private void updateFullPathField() {
    final String sourceDirectoryPath = getSourceDirectoryPath();
    if (sourceDirectoryPath != null) {
      myTfFullPath.setText(sourceDirectoryPath.replace('/', File.separatorChar));
    }
    else {
      myTfFullPath.setText("");
    }
  }

  private JComponent createComponentForChooseSources() {
    final JPanel panel = new JPanel(new GridBagLayout());
    mySourcePathsChooser = new ElementsChooser<JavaModuleSourceRoot>(true) {
      public String getItemText(@NotNull JavaModuleSourceRoot sourceRoot) {
        StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          builder.append(sourceRoot.getDirectory().getAbsolutePath());
          final String packagePrefix = sourceRoot.getPackagePrefix();
          if (!packagePrefix.isEmpty()) {
            builder.append(" (").append(packagePrefix).append(")");
          }
          builder.append(" [").append(sourceRoot.getRootTypeName()).append("]");
          return builder.toString();
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
    };
    final String text = IdeBundle.message("label.java.source.files.have.been.found");
    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                            JBUI.insets(8, 10, 0, 10), 0, 0));
    panel.add(mySourcePathsChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                           JBUI.insets(8, 10), 0, 0));

    final JButton markAllButton = new JButton(IdeBundle.message("button.mark.all"));
    panel.add(markAllButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                    JBUI.insets(0, 10, 8, 2), 0, 0));

    final JButton unmarkAllButton = new JButton(IdeBundle.message("button.unmark.all"));
    panel.add(unmarkAllButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                      JBUI.insets(0, 0, 8, 10), 0, 0));

    markAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySourcePathsChooser.setAllElementsMarked(true);
      }
    });
    unmarkAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySourcePathsChooser.setAllElementsMarked(false);
      }
    });

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myRbCreateSource.isSelected()? myTfSourceDirectoryName : mySourcePathsChooser.getComponent();
  }

  public void updateDataModel() {
    List<Pair<String,String>> paths = null;
    if (CHOOSE_SOURCE_PANEL.equals(myCurrentMode)) {
      final List<JavaModuleSourceRoot> selectedElements = mySourcePathsChooser.getMarkedElements();
      if (selectedElements.size() > 0) {
        paths = new ArrayList<>(selectedElements.size());

        for (final JavaModuleSourceRoot root : selectedElements) {
          paths.add(Pair.create(FileUtil.toSystemIndependentName(root.getDirectory().getAbsolutePath()), root.getPackagePrefix()));
        }
      }
    }
    else {
      if (myRbCreateSource.isSelected()) {
        final String sourceDirectoryPath = getSourceDirectoryPath();
        if (sourceDirectoryPath != null) {
          paths = Collections.singletonList(Pair.create(sourceDirectoryPath, ""));
        }
      }
    }
    myBuilder.setContentEntryPath(getContentRootPath());
    if (paths != null) {
      myBuilder.setSourcePaths(paths);
    }
    else {
      myBuilder.setSourcePaths(new ArrayList<>());
    }
  }

  public boolean validate() throws ConfigurationException {
    if (!super.validate()) {
      return false;
    }

    if (CREATE_SOURCE_PANEL.equals(myCurrentMode) && myRbCreateSource.isSelected()) {
      final String sourceDirectoryPath = getSourceDirectoryPath();
      final String relativePath = myTfSourceDirectoryName.getText().trim();
      if (relativePath.length() == 0) {
        String text = IdeBundle.message("prompt.relative.path.to.sources.empty", FileUtil.toSystemDependentName(sourceDirectoryPath));
        final int answer = Messages.showYesNoCancelDialog(myTfSourceDirectoryName, text, IdeBundle.message("title.mark.source.directory"),
                                               IdeBundle.message("action.mark"), IdeBundle.message("action.do.not.mark"),
                                                 CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
        if (answer == Messages.CANCEL) {
          return false; // cancel
        }
        if (answer == Messages.NO) { // don't mark
          myRbNoSource.doClick();
        }
      }
      if (sourceDirectoryPath != null) {
        final File rootDir = new File(getContentRootPath());
        final File srcDir = new File(sourceDirectoryPath);
        if (!FileUtil.isAncestor(rootDir, srcDir, false)) {
          Messages.showErrorDialog(myTfSourceDirectoryName,
                                   IdeBundle.message("error.source.directory.should.be.under.module.content.root.directory"),
                                   CommonBundle.getErrorTitle());
          return false;
        }
        try {
          VfsUtil.createDirectories(srcDir.getPath());
        }
        catch (IOException e) {
          throw new ConfigurationException(e.getMessage());
        }
      }
    }
    return true;
  }

  @Nullable
  private String getSourceDirectoryPath() {
    final String contentEntryPath = getContentRootPath();
    if (contentEntryPath != null) {
      final String dirName = myTfSourceDirectoryName.getText().trim().replace(File.separatorChar, '/');
      return dirName.length() > 0? contentEntryPath + "/" + dirName : contentEntryPath;
    }
    return null;
  }

  protected boolean shouldRunProgress() {
    return isContentEntryChanged();
  }

  protected void onFinished(final List<JavaModuleSourceRoot> foundPaths, final boolean canceled) {
    if (foundPaths.size() > 0) {
      myCurrentMode = CHOOSE_SOURCE_PANEL;
      mySourcePathsChooser.setElements(foundPaths, true);
    }
    else {
      myCurrentMode = CREATE_SOURCE_PANEL;
      updateFullPathField();
    }
    updateStepUI(canceled ? null : getContentRootPath());
    if (CHOOSE_SOURCE_PANEL.equals(myCurrentMode)) {
      mySourcePathsChooser.selectElements(foundPaths.subList(0, 1));
    }
    else if (CREATE_SOURCE_PANEL.equals(myCurrentMode)) {
      myTfSourceDirectoryName.selectAll();
    }
  }

  private void updateStepUI(final String contentEntryPath) {
    myCurrentContentEntryPath = contentEntryPath;
    ((CardLayout)myResultPanel.getLayout()).show(myResultPanel, myCurrentMode);
    myResultPanel.revalidate();
  }

  protected boolean isContentEntryChanged() {
    final String contentEntryPath = getContentRootPath();
    return myCurrentContentEntryPath == null? contentEntryPath != null : !myCurrentContentEntryPath.equals(contentEntryPath);
  }

  protected List<JavaModuleSourceRoot> calculate() {
    return new ArrayList<>(calculateSourceRoots(getContentRootPath()));
  }

  @NotNull
  public static Collection<JavaModuleSourceRoot> calculateSourceRoots(final String contentRootPath) {
    if (contentRootPath == null) {
      return Collections.emptyList();
    }
    return JavaSourceRootDetectionUtil.suggestRoots(new File(contentRootPath));
  }

  @Nullable
  private String getContentRootPath() {
    return myBuilder.getContentEntryPath();
  }

  protected void setSourceDirectoryName(String name) {
    name = name == null? "" : name.trim();
    myTfSourceDirectoryName.setText(name);
  }

  protected String getProgressText() {
    final String root = getContentRootPath();
    return IdeBundle.message("progress.searching.for.sources", root != null? root.replace('/', File.separatorChar) : "") ;
  }

  private class BrowsePathListener extends BrowseFilesListener {
    private final FileChooserDescriptor myChooserDescriptor;
    private final JTextField myField;

    public BrowsePathListener(JTextField textField, final FileChooserDescriptor chooserDescriptor) {
      super(textField, IdeBundle.message("prompt.select.source.directory"), "", chooserDescriptor);
      myChooserDescriptor = chooserDescriptor;
      myField = textField;
    }

    @Nullable
    private VirtualFile getContentEntryDir() {
      final String contentEntryPath = getContentRootPath();
      if (contentEntryPath != null) {
        return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
          public VirtualFile compute() {
            return LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath);
          }
        });
      }
      return null;
    }

    public void actionPerformed(ActionEvent e) {
      final VirtualFile contentEntryDir = getContentEntryDir();
      if (contentEntryDir != null) {
        myChooserDescriptor.setRoots(contentEntryDir);
        final String textBefore = myField.getText().trim();
        super.actionPerformed(e);
        if (!textBefore.equals(myField.getText().trim())) {
          final String fullPath = myField.getText().trim().replace(File.separatorChar, '/');
          final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(fullPath);
          LOG.assertTrue(fileByPath != null);
          myField.setText(VfsUtilCore.getRelativePath(fileByPath, contentEntryDir, File.separatorChar));
        }
      }
    }
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }

  @Override
  public String getName() {
    return "Path to Sources";
  }
}
