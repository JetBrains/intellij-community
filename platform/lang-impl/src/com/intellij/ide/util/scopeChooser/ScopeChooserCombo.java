/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.projectView.impl.AbstractUrl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ChangeListsScopesProvider;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.PlatformUtils;
import com.intellij.util.TreeItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class ScopeChooserCombo extends ComboboxWithBrowseButton implements Disposable {
  private Project myProject;
  private boolean mySuggestSearchInLibs;
  private boolean myPrevSearchFiles;
  private NamedScopesHolder.ScopeListener myScopeListener;
  private NamedScopeManager myNamedScopeManager;
  private DependencyValidationManager myValidationManager;

  public ScopeChooserCombo() {
    super(new IgnoringComboBox(){
      @Override
      protected boolean isIgnored(Object item) {
        return item instanceof ScopeSeparator;
      }
    });
  }

  public ScopeChooserCombo(final Project project, boolean suggestSearchInLibs, boolean prevSearchWholeFiles, String preselect) {
    this();
    init(project, suggestSearchInLibs, prevSearchWholeFiles,  preselect);
  }

  public void init(final Project project, final String preselect){
    init(project, false, true, preselect);
  }

  public void init(final Project project, final boolean suggestSearchInLibs, final boolean prevSearchWholeFiles,  final String preselect) {
    mySuggestSearchInLibs = suggestSearchInLibs;
    myPrevSearchFiles = prevSearchWholeFiles;
    myProject = project;
    myScopeListener = new NamedScopesHolder.ScopeListener() {
      @Override
      public void scopesChanged() {
        final SearchScope selectedScope = getSelectedScope();
        rebuildModel();
        if (selectedScope != null) {
          selectScope(selectedScope.getDisplayName());
        }
      }
    };
    myNamedScopeManager = NamedScopeManager.getInstance(project);
    myNamedScopeManager.addScopeListener(myScopeListener);
    myValidationManager = DependencyValidationManager.getInstance(project);
    myValidationManager.addScopeListener(myScopeListener);
    addActionListener(createScopeChooserListener());

    final JComboBox combo = getComboBox();
    combo.setRenderer(new ScopeDescriptionWithDelimiterRenderer(combo.getRenderer()));

    rebuildModel();

    selectScope(preselect);
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myValidationManager != null) {
      myValidationManager.removeScopeListener(myScopeListener);
      myValidationManager = null;
    }
    if (myNamedScopeManager != null) {
      myNamedScopeManager.removeScopeListener(myScopeListener);
      myNamedScopeManager = null;
    }
    myScopeListener = null;
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
      @Override
      public void actionPerformed(ActionEvent e) {
        final String selection = getSelectedScopeName();
        final EditScopesDialog dlg = EditScopesDialog.showDialog(myProject, selection);
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
    final DefaultComboBoxModel model = new DefaultComboBoxModel();

    createPredefinedScopeDescriptors(model);

    model.addElement(new ScopeSeparator("VCS Scopes"));
    final List<NamedScope> changeLists = ChangeListsScopesProvider.getInstance(myProject).getCustomScopes();
    for (NamedScope changeListScope : changeLists) {
      final GlobalSearchScope scope = GlobalSearchScopes.filterScope(myProject, changeListScope);
      model.addElement(new ScopeDescriptor(scope));
    }

    final List<ScopeDescriptor> customScopes = new ArrayList<ScopeDescriptor>();
    final NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(myProject);
    for (NamedScopesHolder holder : holders) {
      final NamedScope[] scopes = holder.getEditableScopes();  // predefined scopes already included
      for (NamedScope scope : scopes) {
        final GlobalSearchScope searchScope = GlobalSearchScopes.filterScope(myProject, scope);
        customScopes.add(new ScopeDescriptor(searchScope));
      }
    }
    if (!customScopes.isEmpty()) {
      model.addElement(new ScopeSeparator("Custom Scopes"));
      for (ScopeDescriptor scope : customScopes) {
        model.addElement(scope);
      }
    }

    return model;
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    Dimension preferredSize = super.getPreferredSize();
    return new Dimension(Math.min(400, preferredSize.width), preferredSize.height);
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }
    Dimension minimumSize = super.getMinimumSize();
    return new Dimension(Math.min(200, minimumSize.width), minimumSize.height);
  }

  private void createPredefinedScopeDescriptors(DefaultComboBoxModel model) {
    @SuppressWarnings("deprecation") final DataContext context = DataManager.getInstance().getDataContext();
    for (SearchScope scope : getPredefinedScopes(myProject, context, mySuggestSearchInLibs, myPrevSearchFiles, true, true)) {
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

    if (!PlatformUtils.isCidr() && ModuleUtil.isSupportedRootType(project, JavaSourceRootType.TEST_SOURCE)) { // TODO: fix these scopes in AppCode
      result.add(GlobalSearchScopes.projectProductionScope(project));
      result.add(GlobalSearchScopes.projectTestScope(project));
    }

    result.add(GlobalSearchScopes.openFilesScope(project));

    if (dataContext != null) {
      PsiElement dataContextElement = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (dataContextElement == null) {
        dataContextElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      }
      if (dataContextElement != null) {
        if (!PlatformUtils.isCidr()) { // TODO: have an API to disable module scopes.
          Module module = ModuleUtilCore.findModuleForPsiElement(dataContextElement);
          if (module == null) {
            module = LangDataKeys.MODULE.getData(dataContext);
          }
          if (module != null) {
            result.add(module.getModuleScope());
          }
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
                    if (!(child instanceof PsiWhiteSpace) && child.getContainingFile() != null) {
                      elements.add(child);
                    }
                  }
                  if (!elements.isEmpty()) {
                    SearchScope local = new LocalSearchScope(PsiUtilCore.toPsiElementArray(elements), IdeBundle.message("scope.selection"));
                    result.add(local);
                  }
                }
              }
            }
          }
        }
      }
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
              @Override
              public String getDisplayName() {
                return IdeBundle.message("scope.files.in.previous.search.result");
              }

              @Override
              public boolean contains(@NotNull VirtualFile file) {
                return files.contains(file);
              }

              @Override
              public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
                return 0;
              }

              @Override
              public boolean isSearchInModuleContent(@NotNull Module aModule) {
                return true;
              }

              @Override
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
              if (element != null && element.isValid() && element.getContainingFile() != null) {
                results.add(element);
              }
            }
          }

          if (!results.isEmpty()) {
            result.add(new LocalSearchScope(PsiUtilCore.toPsiElementArray(results), IdeBundle.message("scope.previous.search.results")));
          }
        }
      }
    }

    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    if (favoritesManager != null) {
      for (final String favorite : favoritesManager.getAvailableFavoritesListNames()) {
        final Collection<TreeItem<Pair<AbstractUrl,String>>> rootUrls = favoritesManager.getFavoritesListRootUrls(favorite);
        if (rootUrls.isEmpty()) continue;  // ignore unused root
        result.add(new GlobalSearchScope(project) {
          @Override
          public String getDisplayName() {
            return "Favorite \'" + favorite + "\'";
          }

          @Override
          public boolean contains(@NotNull final VirtualFile file) {
            return favoritesManager.contains(favorite, file);
          }

          @Override
          public int compare(@NotNull final VirtualFile file1, @NotNull final VirtualFile file2) {
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
    }

    if (dataContext != null) {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null) {
        final List<VirtualFile> openFiles = Arrays.asList(files);
        result.add(new DelegatingGlobalSearchScope(GlobalSearchScope.filesScope(project, openFiles)){
          @Override
          public String getDisplayName() {
            return "Selected Files";
          }
        });
      }
    }

    return result;
  }

  @Nullable
  public SearchScope getSelectedScope() {
    final JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    return idx < 0 ? null : ((ScopeDescriptor)combo.getSelectedItem()).getScope();
  }

  @Nullable
  public String getSelectedScopeName() {
    final JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    return idx < 0 ? null : ((ScopeDescriptor)combo.getSelectedItem()).getDisplay();
  }

  private static class ScopeSeparator extends ScopeDescriptor {
    private final String myText;

    public ScopeSeparator(final String text) {
      super(null);
      myText = text;
    }

    @Override
    public String getDisplay() {
      return myText;
    }
  }

  private static class ScopeDescriptionWithDelimiterRenderer extends ListCellRendererWrapper<ScopeDescriptor> {
    public ScopeDescriptionWithDelimiterRenderer(final ListCellRenderer original) {
      super();
    }

    @Override
    public void customize(JList list, ScopeDescriptor value, int index, boolean selected, boolean hasFocus) {
      setText(value.getDisplay());
      if (value instanceof ScopeSeparator) {
        setSeparator();
      }
    }
  }
}
