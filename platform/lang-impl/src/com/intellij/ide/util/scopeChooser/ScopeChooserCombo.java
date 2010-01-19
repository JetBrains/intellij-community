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
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
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
import com.intellij.util.ArrayUtil;
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
        if (value instanceof ScopeDescriptor) {
          setText(((ScopeDescriptor)value).getDisplay());
        }
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

    final NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(myProject);
    for (NamedScopesHolder holder : holders) {
      NamedScope[] scopes = holder.getEditableScopes(); //predefined scopes already included
      for (NamedScope scope : scopes) {
        model.addElement(new ScopeDescriptor(GlobalSearchScope.filterScope(myProject, scope)));
      }
    }

    return model;
  }


  private void createPredefinedScopeDescriptors(DefaultComboBoxModel model) {
    for (SearchScope scope : getPredefinedScopes(myProject, DataManager.getInstance().getDataContext(), mySuggestSearchInLibs, myPrevSearchFiles, true, true)) {
      model.addElement(new ScopeDescriptor(scope));
    }
    for (ScopeDescriptorProvider provider : Extensions.getExtensions(ScopeDescriptorProvider.EP_NAME)) {
      for (ScopeDescriptor scopeDescriptor : provider.getScopeDescriptors(myProject)) {
        model.addElement(scopeDescriptor);
      }
    }
  }

  public static List<SearchScope> getPredefinedScopes(@NotNull final Project project,
                                                      @Nullable final DataContext dataContext,
                                                      boolean suggestSearchInLibs,
                                                      boolean prevSearchFiles,
                                                      boolean currentSelection,
                                                      boolean usageView) {
    ArrayList<SearchScope> result = new ArrayList<SearchScope>();
    result.add(GlobalSearchScope.projectScope(project));
    if (suggestSearchInLibs) {
      result.add(GlobalSearchScope.allScope(project));
    }
    result.add(GlobalSearchScope.projectProductionScope(project));
    result.add(GlobalSearchScope.projectTestScope(project));

    if (dataContext != null) {
      PsiElement dataContextElement = getDataContextElement(dataContext);

      if (dataContextElement != null) {
        Module module = ModuleUtil.findModuleForPsiElement(dataContextElement);
        if (module == null) {
          module = LangDataKeys.MODULE.getData(dataContext);
        }
        if (module != null) {
          result.add(module.getModuleScope());
        }
        if (dataContextElement.getContainingFile() != null) {
          result.add(new LocalSearchScope(dataContextElement, IdeBundle.message("scope.current.file")));
        }
      }
    }

    if (currentSelection) {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
      final Editor selectedTextEditor = fileEditorManager.getSelectedTextEditor();
      if (selectedTextEditor != null) {
        final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(selectedTextEditor.getDocument());
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
                    SearchScope local = new LocalSearchScope(elements.toArray(new PsiElement[elements.size()]), IdeBundle.message("scope.selection"));
                    result.add(local);
                  }
                }
              }
            }
          }
        }
      }
    }

    if (!ChangeListManager.getInstance(project).getAffectedFiles().isEmpty()) {
      GlobalSearchScope modified = new GlobalSearchScope(project) {
        public String getDisplayName() {
          return IdeBundle.message("scope.modified.files");
        }

        public boolean contains(VirtualFile file) {
          return ChangeListManager.getInstance(project).getChange(file) != null;
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

      result.add(modified);
    }

    if (usageView) {
      UsageView selectedUsageView = UsageViewManager.getInstance(project).getSelectedUsageView();
      if (selectedUsageView != null && !selectedUsageView.isSearchInProgress()) {
        final Set<Usage> usages = selectedUsageView.getUsages();
        final List<PsiElement> results = new ArrayList<PsiElement>(usages.size());

        if (prevSearchFiles) {
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
            GlobalSearchScope prev = new GlobalSearchScope(project) {
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
            };
            result.add(prev);
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
            result.add(new LocalSearchScope(results.toArray(new PsiElement[results.size()]), IdeBundle.message("scope.previous.search.results")));
          }
        }
      }
    }

    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    String[] favoritesLists = favoritesManager == null ? ArrayUtil.EMPTY_STRING_ARRAY : favoritesManager.getAvailableFavoritesLists();
    for (final String favorite : favoritesLists) {
      result.add(new GlobalSearchScope(project) {
        @Override
        public String getDisplayName() {
          return "Favorite \'" + favorite + "\'";
        }

        @Override
        public boolean contains(final VirtualFile file) {
          return favoritesManager.contains(favorite, file);
        }

        @Override
        public int compare(final VirtualFile file1, final VirtualFile file2) {
          return 0;
        }

        @Override
        public boolean isSearchInModuleContent(@NotNull final Module aModule) {
          return true;
        }

        @Override
        public boolean isSearchInLibraries() {
          return true;
        }
      });
    }
    return result;
  }

  public static PsiElement getDataContextElement(DataContext dataContext) {
    PsiElement dataContextElement = LangDataKeys.PSI_FILE.getData(dataContext);

    if (dataContextElement == null) {
      dataContextElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    return dataContextElement;
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
