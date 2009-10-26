/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packaging.impl.ui.properties;

import com.intellij.CommonBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.elements.CompositeElementWithManifest;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public abstract class ElementWithManifestPropertiesPanel<E extends CompositeElementWithManifest<?>> extends PackagingElementPropertiesPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packaging.impl.ui.properties.ElementWithManifestPropertiesPanel");
  private final E myElement;
  private final ArtifactEditorContext myContext;
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myMainClassField;
  private TextFieldWithBrowseButton myClasspathField;
  private JLabel myTitleLabel;
  private JButton myRemoveFromArtifactButton;
  private JButton myCreateManifestButton;
  private JButton myUseExistingManifestButton;
  private JPanel myPropertiesPanel;
  private JTextField myManifestPathField;
  private JLabel myManifestNotFoundLabel;
  private ManifestFileConfiguration myManifestFileConfiguration;

  public ElementWithManifestPropertiesPanel(E element, final ArtifactEditorContext context) {
    myElement = element;
    myManifestFileConfiguration = context.getManifestFile(element, context.getArtifactType());
    myContext = context;

    myMainClassField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = context.getProject();
        final TreeClassChooserFactory chooserFactory = TreeClassChooserFactory.getInstance(project);
        final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
        final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myMainClassField.getText(), searchScope);
        final TreeClassChooser chooser =
            chooserFactory.createWithInnerClassesScopeChooser("Select Main Class", searchScope, new MainClassFilter(), aClass);
        chooser.showDialog();
        final PsiClass selected = chooser.getSelectedClass();
        if (selected != null) {
          myMainClassField.setText(selected.getQualifiedName());
        }
      }
    });

    myClasspathField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(myClasspathField.getTextField(), "Edit Classpath", "classpath-attribute-editor");
      }
    });
    myClasspathField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myContext.queueValidation();
      }
    });
    myUseExistingManifestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        chooseManifest();
      }
    });
    myCreateManifestButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        createManifest();
      }
    });

    //todo[nik] do we really need this button?
    myRemoveFromArtifactButton.setVisible(false);
    myRemoveFromArtifactButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        removeManifest();
      }
    });
  }

  private void removeManifest() {
  }

  private void createManifest() {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle("Select Directory for META-INF/MANIFEST.MF file");
    final VirtualFile[] files = FileChooser.chooseFiles(myContext.getProject(), descriptor, ManifestFileUtil.suggestManifestFileDirectory(myElement, myContext, myContext.getArtifactType()));
    if (files.length != 1) return;

    final Ref<IOException> exc = Ref.create(null);
    final VirtualFile file = new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) {
        VirtualFile dir = files[0];
        try {
          if (!dir.getName().equals(ManifestFileUtil.MANIFEST_DIR_NAME)) {
            VirtualFile newDir = dir.findChild(ManifestFileUtil.MANIFEST_DIR_NAME);
            if (newDir == null) {
              newDir = dir.createChildDirectory(this, ManifestFileUtil.MANIFEST_DIR_NAME);
            }
            dir = newDir;
          }
          result.setResult(dir.createChildData(this, ManifestFileUtil.MANIFEST_FILE_NAME));
        }
        catch (IOException e) {
          exc.set(e);
        }
      }
    }.execute().getResultObject();

    final IOException exception = exc.get();
    if (exception != null) {
      LOG.info(exception);
      Messages.showErrorDialog(myMainPanel, exception.getMessage(), CommonBundle.getErrorTitle());
      return;
    }

    PackagingElementFactory.getInstance().addFileCopy(myElement, ManifestFileUtil.MANIFEST_DIR_NAME, file.getPath());
    myContext.getThisArtifactEditor().updateLayoutTree();
    updateComponents(new ManifestFileConfiguration(null, null, file.getPath()));
    apply();
  }

  private void chooseManifest() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() ||
               file.getName().equalsIgnoreCase(ManifestFileUtil.MANIFEST_FILE_NAME));
      }
    };
    descriptor.setTitle("Specify Path to MANIFEST.MF file");
    final VirtualFile[] files = FileChooser.chooseFiles(myContext.getProject(), descriptor);
    if (files.length != 1) return;

    final String path = files[0].getPath();
    PackagingElementFactory.getInstance().addFileCopy(myElement, ManifestFileUtil.MANIFEST_DIR_NAME, path);
    myContext.getThisArtifactEditor().updateLayoutTree();
    updateComponents(ManifestFileUtil.createManifestFileConfiguration(files[0]));
    apply();
  }

  private void updateComponents(@NotNull ManifestFileConfiguration configuration) {
    final String manifestFilePath = configuration.getManifestFilePath();
    final String card;
    if (manifestFilePath != null) {
      card = "properties";
      myManifestPathField.setText(FileUtil.toSystemDependentName(manifestFilePath));
      myMainClassField.setText(StringUtil.notNullize(configuration.getMainClass()));
      myClasspathField.setText(StringUtil.join(configuration.getClasspath(), " "));
    }
    else {
      card = "buttons";
      myManifestPathField.setText("");
    }
    ((CardLayout)myPropertiesPanel.getLayout()).show(myPropertiesPanel, card);
  }

  public void reset() {
    myTitleLabel.setText("'" + myElement.getName() + "' manifest properties:");
    myManifestNotFoundLabel.setText("Manifest.mf file not found in '" + myElement.getName() + "'");
    final VirtualFile file = ManifestFileUtil.findManifestFile(myElement, myContext, myContext.getArtifactType());
    String path = file != null ? file.getPath() : null;
    if (!Comparing.equal(path, myManifestFileConfiguration.getManifestFilePath())) {
      myManifestFileConfiguration.copyFrom(ManifestFileUtil.createManifestFileConfiguration(file));
    }
    updateComponents(myManifestFileConfiguration);
  }

  public boolean isModified() {
    return !myManifestFileConfiguration.getClasspath().equals(getConfiguredClasspath())
           || !Comparing.equal(myManifestFileConfiguration.getMainClass(), getConfiguredMainClass())
           || !Comparing.equal(myManifestFileConfiguration.getManifestFilePath(), getConfiguredManifestPath());
  }

  @Nullable
  private String getConfiguredManifestPath() {
    final String path = myManifestPathField.getText();
    return path.length() != 0 ? FileUtil.toSystemIndependentName(path) : null;
  }

  @Override
  public void apply() {
    myManifestFileConfiguration.setMainClass(getConfiguredMainClass());
    myManifestFileConfiguration.setClasspath(getConfiguredClasspath());
    myManifestFileConfiguration.setManifestFilePath(getConfiguredManifestPath());
  }

  private List<String> getConfiguredClasspath() {
    return StringUtil.split(myClasspathField.getText(), " ");
  }

  @NotNull
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Nullable
  private String getConfiguredMainClass() {
    final String className = myMainClassField.getText();
    return className.length() != 0 ? className : null;
  }

  protected static class MainClassFilter implements TreeClassChooser.ClassFilter {
    public boolean isAccepted(PsiClass aClass) {
      return PsiMethodUtil.MAIN_CLASS.value(aClass) && PsiMethodUtil.hasMainMethod(aClass);
    }
  }
}
