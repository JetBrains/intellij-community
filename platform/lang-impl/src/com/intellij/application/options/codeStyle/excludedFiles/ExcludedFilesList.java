// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel;
import com.intellij.formatting.fileSet.FileSetDescriptor;
import com.intellij.ide.util.scopeChooser.EditScopesDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.*;

public class ExcludedFilesList extends JBList<FileSetDescriptor> {

  private final ToolbarDecorator myFileListDecorator;
  private DefaultListModel<FileSetDescriptor> myModel;
  private @Nullable CodeStyleSchemesModel mySchemesModel;

  public ExcludedFilesList() {
    super();
    myFileListDecorator = ToolbarDecorator.createDecorator(this)
      .setRemoveAction(
        new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            removeDescriptor();
          }
        })
      .disableUpDownActions();
    addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        onSelectionChange();
      }
    });
  }

  public void initModel() {
    myModel = createDefaultListModel(new FileSetDescriptor[0]);
    setModel(myModel);
  }

  private void onSelectionChange() {
    int i = getSelectedIndex();
    AnActionButton removeButton = ToolbarDecorator.findRemoveButton(myFileListDecorator.getActionsPanel());
    if (removeButton != null) {
      removeButton.setEnabled(i >= 0);
    }
  }

  public void reset(@NotNull CodeStyleSettings settings) {
    myModel.clear();
    for (FileSetDescriptor descriptor : settings.getExcludedFiles().getDescriptors(NamedScopeDescriptor.NAMED_SCOPE_TYPE)) {
      if (NamedScopeToGlobConverter.convert((NamedScopeDescriptor)descriptor) == null) {
        myModel.addElement(descriptor);
      }
    }
  }

  public void apply(@NotNull CodeStyleSettings settings) {
    settings.getExcludedFiles().setDescriptors(NamedScopeDescriptor.NAMED_SCOPE_TYPE, getDescriptors());
  }

  @NotNull
  private List<FileSetDescriptor> getDescriptors() {
    List<FileSetDescriptor> descriptors = new ArrayList<>();
    for (int i = 0; i < myModel.getSize(); i++) {
      descriptors.add(myModel.get(i));
    }
    Collections.sort(descriptors, (d1, d2) -> {
      int result = StringUtil.compare(d1.getName(), d2.getName(), false);
      if (result != 0) return result;
      return StringUtil.compare(d1.getPattern(), d2.getPattern(), false);
    });
    return descriptors;
  }

  public boolean isModified(@NotNull CodeStyleSettings settings) {
    return !settings.getExcludedFiles().getDescriptors(NamedScopeDescriptor.NAMED_SCOPE_TYPE).equals(getDescriptors());
  }

  public ToolbarDecorator getDecorator() {
    return myFileListDecorator;
  }

  @SuppressWarnings("unused")
  private void addDescriptor() {
    assert mySchemesModel != null;
    List<NamedScope> availableScopes = getAvailableScopes();
    if (!availableScopes.isEmpty()) {
      ExcludedFilesScopeDialog dialog = new ExcludedFilesScopeDialog(mySchemesModel.getProject(), availableScopes);
      dialog.show();
      if (dialog.isOK()) {
        FileSetDescriptor descriptor = dialog.getDescriptor();
        if (descriptor != null) {
          int insertAt = getSelectedIndex() < 0 ? getItemsCount() : getSelectedIndex() + 1;
          int exiting = myModel.indexOf(descriptor);
          if (exiting < 0) {
            myModel.add(insertAt, descriptor);
            setSelectedValue(descriptor, true);
          }
          else {
            setSelectedValue(myModel.get(exiting), true);
          }
        }
      }
      else if (dialog.getExitCode() == ExcludedFilesScopeDialog.EDIT_SCOPES) {
        editScope(null);
      }
    }
    else {
      editScope(null);
    }
  }

  private List<NamedScope> getAvailableScopes() {
    Set<String> usedNames = getUsedScopeNames();
    List<NamedScope> namedScopes = new ArrayList<>();
    for (NamedScopesHolder holder : getScopeHolders()) {
      for (NamedScope scope : holder.getEditableScopes()) {
        if (!usedNames.contains(scope.getScopeId())) {
          namedScopes.add(scope);
        }
      }
    }
    return namedScopes;
  }

  private Set<String> getUsedScopeNames() {
    Set<String> usedScopeNames = new HashSet<>();
    for (int i =0 ; i < myModel.size(); i ++) {
      FileSetDescriptor descriptor = myModel.get(i);
      if (descriptor instanceof NamedScopeDescriptor) {
        usedScopeNames.add(descriptor.getName());
      }
    }
    return usedScopeNames;
  }

  private void removeDescriptor() {
    int i = getSelectedIndex();
    if (i >= 0) {
      myModel.remove(i);
    }
  }

  @SuppressWarnings("unused")
  private void editDescriptor() {
    int i = getSelectedIndex();
    FileSetDescriptor selectedDescriptor = i >= 0 ? myModel.get(i) : null;
    if (selectedDescriptor instanceof NamedScopeDescriptor) {
      ensureScopeExists((NamedScopeDescriptor)selectedDescriptor);
      editScope(selectedDescriptor.getName());
    }
    else {
      editScope(null);
    }
  }

  public void setSchemesModel(@NotNull CodeStyleSchemesModel schemesModel) {
    mySchemesModel = schemesModel;
  }

  public void editScope(@Nullable final String selectedName) {
    assert mySchemesModel != null;
    EditScopesDialog scopesDialog = EditScopesDialog.showDialog(getScopeHolderProject(), selectedName);
    if (scopesDialog.isOK()) {
      NamedScope scope = scopesDialog.getSelectedScope();
      if (scope != null) {
        String newName = scope.getScopeId();
        FileSetDescriptor newDesciptor = null;
        if (selectedName == null) {
          newDesciptor = findDescriptor(newName);
          if (newDesciptor == null) {
            newDesciptor = new NamedScopeDescriptor(scope);
            myModel.addElement(newDesciptor);
          }
        }
        else {
          FileSetDescriptor oldDescriptor = findDescriptor(selectedName);
          if (!selectedName.equals(newName)) {
            int index = myModel.indexOf(oldDescriptor);
            myModel.removeElement(oldDescriptor);
            newDesciptor = findDescriptor(newName);
            if (newDesciptor == null) {
              newDesciptor = new NamedScopeDescriptor(scope);
              myModel.add(index, newDesciptor);
            }
          }
          else if (oldDescriptor != null) {
            PackageSet fileSet = scope.getValue();
            oldDescriptor.setPattern(fileSet != null ? fileSet.getText() : null);
          }
        }
        if (newDesciptor != null) {
          setSelectedValue(newDesciptor, true);
        }
      }
    }
  }

  private void ensureScopeExists(@NotNull NamedScopeDescriptor descriptor) {
    List<NamedScopesHolder> holders = getScopeHolders();
    for (NamedScopesHolder holder : holders) {
      if (holder.getScope(descriptor.getName()) != null) return;
    }
    NamedScopesHolder projectScopeHolder = DependencyValidationManager.getInstance(getScopeHolderProject());
    NamedScope newScope = projectScopeHolder.createScope(descriptor.getName(), descriptor.getFileSet());
    projectScopeHolder.addScope(newScope);
  }

  private Project getScopeHolderProject() {
    assert mySchemesModel != null;
    CodeStyleScheme scheme = mySchemesModel.getSelectedScheme();
    return mySchemesModel.isProjectScheme(scheme) ? mySchemesModel.getProject() : ProjectManager.getInstance().getDefaultProject();
  }

  @Nullable
  private FileSetDescriptor findDescriptor(@NotNull String name) {
    for (int i = 0; i < myModel.size(); i++) {
      if (name.equals(myModel.get(i).getName())) return myModel.get(i);
    }
    return null;
  }

  private List<NamedScopesHolder> getScopeHolders() {
    List<NamedScopesHolder> holders = new ArrayList<>();
    Project project = getScopeHolderProject();
    holders.add(DependencyValidationManager.getInstance(project));
    holders.add(NamedScopeManager.getInstance(project));
    return holders;
  }
}
