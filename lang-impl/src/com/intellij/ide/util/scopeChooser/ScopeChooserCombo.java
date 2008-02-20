/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.rules.PsiElementUsage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScopeChooserCombo extends ComboboxWithBrowseButton {
  private Project myProject;
  private boolean mySuggestSearchInLibs;
  private boolean myPrevSearchFiles;


  public ScopeChooserCombo() {
  }

  public ScopeChooserCombo(final Project project, boolean suggestSearchInLibs, boolean prevSearchWholeFiles, String preselect) {
    init(project, suggestSearchInLibs, prevSearchWholeFiles,  preselect);
  }

  public void init(final Project project, final String preselect){
    init(project, false, true, preselect);    
  }

  public void init(final Project project, final boolean suggestSearchInLibs, final boolean prevSearchWholeFiles,  final String preselect) {
    mySuggestSearchInLibs = suggestSearchInLibs;
    myPrevSearchFiles = prevSearchWholeFiles;
    final JComboBox combo = getComboBox();
    myProject = project;
    addActionListener(createScopeChooserListener());

    combo.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(((ScopeDescriptor)value).getDisplay());
        return this;
      }
    });

    rebuildModel();

    selectScope(preselect);
  }

  private void selectScope(String preselect) {
    if (preselect != null) {
      final JComboBox combo = getComboBox();
      DefaultComboBoxModel model = (DefaultComboBoxModel)combo.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        ScopeDescriptor descriptor = (ScopeDescriptor)model.getElementAt(i);
        if (preselect.equals(descriptor.getDisplay())) {
          combo.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  private ActionListener createScopeChooserListener() {
    return new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final String selection = getSelectedScopeName();
        final ScopeChooserConfigurable chooserConfigurable = ScopeChooserConfigurable.getInstance(myProject);
        final EditScopesDialog dlg = EditScopesDialog.editConfigurable(myProject, new Runnable() {
          public void run() {
            if (selection != null) {
              chooserConfigurable.selectNodeInTree(selection);
            }
          }
        });
        if (dlg.isOK()){
          rebuildModel();
          final NamedScope namedScope = dlg.getSelectedScope();
          if (namedScope != null) {
            selectScope(namedScope.getName());
          }
        }
      }
    };
  }

  private void rebuildModel() {
    getComboBox().setModel(createModel());
  }

  private DefaultComboBoxModel createModel() {
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    createPredefinedScopeDescriptors(model);

    final NamedScopesHolder[] holders = myProject.getComponents(NamedScopesHolder.class);
    for (NamedScopesHolder holder : holders) {
      NamedScope[] scopes = holder.getEditableScopes(); //predefined scopes already included
      for (NamedScope scope : scopes) {
        model.addElement(new ScopeDescriptor(GlobalSearchScope.filterScope(myProject, scope)));
      }
    }

    return model;
  }

  private void createPredefinedScopeDescriptors(DefaultComboBoxModel model) {
    model.addElement(new ScopeDescriptor(GlobalSearchScope.projectScope(myProject)));
    if (mySuggestSearchInLibs) {
      model.addElement(new ScopeDescriptor(GlobalSearchScope.allScope(myProject)));
    }
    model.addElement(new ScopeDescriptor(GlobalSearchScope.projectProductionScope(myProject)));
    model.addElement(new ScopeDescriptor(GlobalSearchScope.projectTestScope(myProject)));

    final DataContext dataContext = DataManager.getInstance().getDataContext();
    final PsiElement dataContextElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (dataContextElement != null) {
      Module module = ModuleUtil.findModuleForPsiElement(dataContextElement);
      if (module == null) {
        module = LangDataKeys.MODULE.getData(dataContext);
      }
      if (module != null) {
        model.addElement(new ScopeDescriptor(module.getModuleScope()));
      }
      if (dataContextElement.getContainingFile() != null) {
        model.addElement(new ScopeDescriptor(new LocalSearchScope(dataContextElement, IdeBundle.message("scope.current.file"))));
      }
    }

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    final Editor selectedTextEditor = fileEditorManager.getSelectedTextEditor();
    if (selectedTextEditor != null) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(selectedTextEditor.getDocument());
      if (psiFile != null) {
        if (selectedTextEditor.getSelectionModel().hasSelection()) {
          final PsiElement startElement = psiFile.findElementAt(selectedTextEditor.getSelectionModel().getSelectionStart());
          if (startElement != null) {
            final PsiElement endElement = psiFile.findElementAt(selectedTextEditor.getSelectionModel().getSelectionEnd());
            if (endElement != null) {
              final PsiElement parent = PsiTreeUtil.findCommonParent(startElement, endElement);
              if (parent != null) {
                final List<PsiElement> elements = new ArrayList<PsiElement>();
                final PsiElement[] children = parent.getChildren();
                for (PsiElement child : children) {
                  if (!(child instanceof PsiWhiteSpace)) {
                    elements.add(child);
                  }
                }
                if (!elements.isEmpty()) {
                  model.addElement(new ScopeDescriptor(new LocalSearchScope(elements.toArray(new PsiElement[elements.size()]), IdeBundle.message("scope.selection"))));
                }
              }
            }
          }
        }
      }
    }

    if (!ChangeListManager.getInstance(myProject).getAffectedFiles().isEmpty()) {
      model.addElement(new ModifiedFilesScopeDescriptor());
    }

    UsageView selectedUsageView = UsageViewManager.getInstance(myProject).getSelectedUsageView();

    if (selectedUsageView != null && !selectedUsageView.isSearchInProgress()) {
      final Set<Usage> usages = selectedUsageView.getUsages();
      final List<PsiElement> results = new ArrayList<PsiElement>(usages.size());

      if (myPrevSearchFiles) {
        final Set<VirtualFile> files = new HashSet<VirtualFile>();
        for (Usage usage : usages) {
          if (usage instanceof PsiElementUsage) {
            PsiElement psiElement = ((PsiElementUsage)usage).getElement();
            if (psiElement != null && psiElement.isValid()) {
              PsiFile psiFile = psiElement.getContainingFile();
              if (psiFile != null) {
                VirtualFile file = psiFile.getVirtualFile();
                if (file != null) files.add(file);
              }
            }
          }
        }
        if (!files.isEmpty()) {
          model.addElement(new ScopeDescriptor(new GlobalSearchScope() {
            public String getDisplayName() {
              return IdeBundle.message("scope.files.in.previous.search.result");
            }

            public boolean contains(VirtualFile file) {
              return files.contains(file);
            }

            public int compare(VirtualFile file1, VirtualFile file2) {
              return 0;
            }

            public boolean isSearchInModuleContent(@NotNull Module aModule) {
              return true;
            }

            public boolean isSearchInLibraries() {
              return true;
            }
          }));
        }
      }
      else {
        for (Usage usage : usages) {
          if (usage instanceof PsiElementUsage) {
            final PsiElement element = ((PsiElementUsage)usage).getElement();
            if (element != null && element.isValid()) {
              results.add(element);
            }
          }
        }

        if (!results.isEmpty()) {
          model.addElement(new ScopeDescriptor(new LocalSearchScope(results.toArray(new PsiElement[results.size()]),
                                                                    IdeBundle.message("scope.previous.search.results"))));
        }
      }
    }

    for (ScopeDescriptorProvider provider : Extensions.getExtensions(ScopeDescriptorProvider.EP_NAME)) {
      for (ScopeDescriptor scopeDescriptor : provider.getScopeDescriptors(myProject)) {
        model.addElement(scopeDescriptor);
      }
    }
  }

  class ModifiedFilesScopeDescriptor extends ScopeDescriptor {

    public ModifiedFilesScopeDescriptor() {
      super(null);
    }

    public String getDisplay() {
      return IdeBundle.message("scope.modified.files");
    }

    public SearchScope getScope() {
      return new GlobalSearchScope() {
        public String getDisplayName() {
          return getDisplay();
        }

        public boolean contains(VirtualFile file) {
          return ChangeListManager.getInstance(myProject).getChange(file) != null;
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          return 0;
        }

        public boolean isSearchInModuleContent(@NotNull Module aModule) {
          return true;
        }

        public boolean isSearchInLibraries() {
          return false;
        }
      };
    }
  }

  @Nullable
  public SearchScope getSelectedScope() {
    JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    if (idx < 0) return null;
    return ((ScopeDescriptor)combo.getSelectedItem()).getScope();
  }

  @Nullable
  public String getSelectedScopeName() {
    JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    if (idx < 0) return null;
    return ((ScopeDescriptor)combo.getSelectedItem()).getDisplay();
  }

}