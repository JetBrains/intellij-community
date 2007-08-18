package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.InheritedMembersFilter;
import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.structureView.impl.jsp.StructureViewComposite;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper;
import com.intellij.ide.util.FileStructureDialog;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.Language;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.structureView.PropertiesFileStructureViewModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public class ViewStructureAction extends AnAction implements TreeActionsOwner {
  public ViewStructureAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    final Editor editor = DataKeys.EDITOR.getData(dataContext);
    final FileEditor fileEditor = DataKeys.FILE_EDITOR.getData(dataContext);
    if (editor == null) return;
    if (fileEditor == null) return;

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.file.structure");

    Navigatable navigatable = DataKeys.NAVIGATABLE.getData(dataContext);
    DialogWrapper dialog = createDialog(psiFile, editor, project, navigatable, fileEditor);
    if (dialog != null) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        dialog.setTitle(virtualFile.getPresentableUrl());
      }
      dialog.show();
    }
  }

  private DialogWrapper createDialog(PsiFile psiFile,
                                     final Editor editor, Project project, Navigatable navigatable, final FileEditor fileEditor) {
    final StructureViewModel structureViewModel;
    Disposable auxDisposable = null;

    final StructureViewBuilder structureViewBuilder = fileEditor.getStructureViewBuilder();

    if (structureViewBuilder instanceof TreeBasedStructureViewBuilder) {
      structureViewModel = ((TreeBasedStructureViewBuilder)structureViewBuilder).createStructureViewModel();
    }
    else if (psiFile instanceof PropertiesFile) {
      structureViewModel = new PropertiesFileStructureViewModel((PropertiesFile)psiFile);
    }
    else if (PsiUtil.isInJspFile(psiFile)) {
      Language language = ((LanguageFileType)psiFile.getFileType()).getLanguage();
      StructureViewComposite structureViewComposite =
        (StructureViewComposite)language.getStructureViewBuilder(psiFile).createStructureView(fileEditor, project);
      StructureView structureView = structureViewComposite.getSelectedStructureView();
      structureViewModel = ((StructureViewComponent)structureView).getTreeModel();
      auxDisposable = structureViewComposite;
    }
    else {
      return null;
    }

    if (auxDisposable == null) {
      auxDisposable = new Disposable() {
        public void dispose() {
          structureViewModel.dispose();
        }
      };
    }

    return createStructureViewBasedDialog(structureViewModel, editor, project, navigatable, auxDisposable);
  }

  public FileStructureDialog createStructureViewBasedDialog(final StructureViewModel structureViewModel,
                                                            final Editor editor,
                                                            final Project project,
                                                            final Navigatable navigatable,
                                                            final @NotNull Disposable alternativeDisposable) {
    return new FileStructureDialog(new TreeModelWrapper(structureViewModel, this), editor, project, navigatable, alternativeDisposable);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      presentation.setEnabled(false);
      return;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();

    if (virtualFile == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(
      virtualFile.getFileType().getStructureViewBuilder(virtualFile, project) != null );
  }

  public void setActionActive(String name, boolean state) {

  }

  public boolean isActionActive(String name) {
    return InheritedMembersFilter.ID.equals(name) || KindSorter.ID.equals(name) || Sorter.ALPHA_SORTER_ID.equals(name);
  }
}
