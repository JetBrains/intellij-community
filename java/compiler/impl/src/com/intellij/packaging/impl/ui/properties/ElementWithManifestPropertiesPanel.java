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

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.impl.elements.CompositeElementWithManifest;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author nik
 */
public abstract class ElementWithManifestPropertiesPanel<E extends CompositeElementWithManifest<?>> extends PackagingElementPropertiesPanel {
  private final E myElement;
  private final ArtifactEditorContext myContext;
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myMainClassField;
  private TextFieldWithBrowseButton myClasspathField;
  private JLabel myTitleLabel;
  private TextFieldWithBrowseButton myManifestFilePathField;
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

    myMainClassField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        createManifestFileIfNeeded();
      }
    });
    myClasspathField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Messages.showTextAreaDialog(myClasspathField.getTextField(), "Edit Classpath", "classpath-attribute-editor");
      }
    });
    myManifestFilePathField.addBrowseFolderListener("Specify Path to MANIFEST.MF file", "", context.getProject(), new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, showHiddenFiles) && file.isDirectory() || file.getName().equalsIgnoreCase(ManifestFileUtil.MANIFEST_FILE_NAME);
      }
    });
    myClasspathField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        createManifestFileIfNeeded();
        myContext.queueValidation();
      }
    });
  }

  private void createManifestFileIfNeeded() {
    if ((myClasspathField.getText().trim().length() > 0 || myMainClassField.getText().trim().length() > 0)
        && myManifestFilePathField.getText().length() == 0) {
      final String path = ManifestFileUtil.suggestManifestFilePathAndAddElement(myElement, myContext, myContext.getArtifactType());
      myManifestFilePathField.setText(FileUtil.toSystemDependentName(path));
    }
  }

  public void reset() {
    myTitleLabel.setText("'" + myElement.getName() + "' manifest properties:");
    myMainClassField.setText(StringUtil.notNullize(myManifestFileConfiguration.getMainClass()));
    myClasspathField.setText(StringUtil.join(myManifestFileConfiguration.getClasspath(), " "));
    myManifestFilePathField.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(myManifestFileConfiguration.getManifestFilePath())));
    createManifestFileIfNeeded();
  }

  public boolean isModified() {
    return !myManifestFileConfiguration.getClasspath().equals(getConfiguredClasspath())
           || !Comparing.equal(myManifestFileConfiguration.getMainClass(), getConfiguredMainClass())
           || !Comparing.equal(myManifestFileConfiguration.getManifestFilePath(), getConfiguredManifestPath());
  }

  @Nullable
  private String getConfiguredManifestPath() {
    final String path = myManifestFilePathField.getText();
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
