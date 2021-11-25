// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveDialogBase;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ven
 */
public class MoveClassesOrPackagesToNewDirectoryDialog extends MoveDialogBase {
  private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesToNewDirectoryDialog.class);

  private final PsiDirectory myDirectory;
  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;

  public MoveClassesOrPackagesToNewDirectoryDialog(@NotNull final PsiDirectory directory, PsiElement[] elementsToMove,
                                                   final MoveCallback moveCallback) {
    this(directory, elementsToMove, true, moveCallback);
  }

  public MoveClassesOrPackagesToNewDirectoryDialog(@NotNull final PsiDirectory directory, PsiElement[] elementsToMove,
                                                   boolean canShowPreserveSourceRoots,
                                                   final MoveCallback moveCallback) {
    super(directory.getProject(), false, MoveClassesOrPackagesDialog.canBeOpenedInEditor(elementsToMove));
    setTitle(MoveHandler.getRefactoringName());
    myDirectory = directory;
    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    myDestDirectoryField.setText(FileUtil.toSystemDependentName(directory.getVirtualFile().getPath()));
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myDestDirectoryField.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final VirtualFile file = FileChooser.chooseFile(descriptor, myDirectory.getProject(), directory.getVirtualFile());
        if (file != null) {
          myDestDirectoryField.setText(FileUtil.toSystemDependentName(file.getPath()));
        }
      }
    });
    mySourceNameLabel.setText(HtmlChunk.html()
                                .addRaw(StringUtil.join(elementsToMove, 
                                                        element -> element instanceof PsiFileSystemItem 
                                                         ? "../" + SymbolPresentationUtil.getFilePathPresentation((PsiFileSystemItem)element) 
                                                         : SymbolPresentationUtil.getSymbolPresentableText(element),
                                                        "<br/>"))
                                .toString());
    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    mySearchInCommentsAndStringsCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_IN_COMMENTS);
    mySearchForTextOccurrencesCheckBox.setSelected(refactoringSettings.MOVE_SEARCH_FOR_TEXT);

    myDestDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
        setOKActionEnabled(myDestDirectoryField.getText().length() > 0);
      }
    });

    if (canShowPreserveSourceRoots) {
      final Set<VirtualFile> sourceRoots = new HashSet<>();
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
      final Module destinationModule = fileIndex.getModuleForFile(directory.getVirtualFile());
      boolean sameModule = true;
      for (PsiElement element : elementsToMove) {
        if (element instanceof PsiPackage) {
          for (PsiDirectory psiDirectory : ((PsiPackage)element).getDirectories()) {
            final VirtualFile virtualFile = psiDirectory.getVirtualFile();
            sourceRoots.add(fileIndex.getSourceRootForFile(virtualFile));
            //sameModule &= destinationModule == fileIndex.getModuleForFile(virtualFile);
          }
        } else if (element instanceof PsiClass) {
          final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
          LOG.assertTrue(virtualFile != null);
          sourceRoots.add(fileIndex.getSourceRootForFile(virtualFile));
          sameModule &= destinationModule == fileIndex.getModuleForFile(virtualFile);
        }
      }
      myPreserveSourceRoot.setVisible(sourceRoots.size() > 1);
      myPreserveSourceRoot.setSelected(sameModule);
    }
    else if (elementsToMove.length < 2) {
      myPreserveSourceRoot.setVisible(false);
      myPreserveSourceRoot.setSelected(false);
    }
    init();
  }

  private TextFieldWithBrowseButton myDestDirectoryField;
  private JCheckBox mySearchForTextOccurrencesCheckBox;
  private JCheckBox mySearchInCommentsAndStringsCheckBox;
  private JPanel myRootPanel;
  private JCheckBox myPreserveSourceRoot;
  private JLabel mySourceNameLabel;

  private boolean isSearchInNonJavaFiles() {
    return mySearchForTextOccurrencesCheckBox.isSelected();
  }

  private boolean isSearchInComments() {
    return mySearchInCommentsAndStringsCheckBox.isSelected();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override
  protected void doAction() {
    final String path = FileUtil.toSystemIndependentName(myDestDirectoryField.getText());
    final Project project = myDirectory.getProject();
    PsiDirectory directory = WriteAction.compute(() -> {
      try {
        return DirectoryUtil.mkdirs(PsiManager.getInstance(project), path);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    });
    if (directory == null) {
      Messages.showErrorDialog(project, JavaRefactoringBundle.message("cannot.find.or.create.destination.directory"),
                               JavaRefactoringBundle.message("cannot.move"));
      return;
    }

    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      Messages.showErrorDialog(project, JavaRefactoringBundle.message("destination.directory.does.not.correspond.to.any.package"),
                               JavaRefactoringBundle.message("cannot.move"));
      return;
    }

    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;
    final BaseRefactoringProcessor refactoringProcessor =
      createRefactoringProcessor(project, directory, aPackage, searchInComments, searchForTextOccurences);
    if (refactoringProcessor != null) {
      invokeRefactoring(refactoringProcessor);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDestDirectoryField.getTextField();
  }

  //for scala plugin
  @NotNull
  protected MoveClassesOrPackagesProcessor createMoveClassesOrPackagesProcessor(Project project,
                                                                          PsiElement[] elements,
                                                                          @NotNull final MoveDestination moveDestination,
                                                                          boolean searchInComments,
                                                                          boolean searchInNonJavaFiles,
                                                                          MoveCallback moveCallback) {

    return new MoveClassesOrPackagesProcessor(project, elements, moveDestination,
        searchInComments, searchInNonJavaFiles, moveCallback);
  }

  protected BaseRefactoringProcessor createRefactoringProcessor(Project project,
                                                                PsiDirectory directory,
                                                                PsiPackage aPackage,
                                                                boolean searchInComments,
                                                                boolean searchForTextOccurences) {
    final MoveDestination destination = createDestination(aPackage, directory);
    if (destination == null) return null;

    MoveClassesOrPackagesProcessor processor = createMoveClassesOrPackagesProcessor(myDirectory.getProject(), myElementsToMove, destination,
        searchInComments, searchForTextOccurences, myMoveCallback);

    processor.setOpenInEditor(isOpenInEditor());
    if (processor.verifyValidPackageName()) {
      return processor;
    }
    return null;
  }

  @Nullable
  protected MoveDestination createDestination(PsiPackage aPackage, PsiDirectory directory) {
    final Project project = aPackage.getProject();
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(directory.getVirtualFile());
    if (sourceRoot == null) {
      Messages.showErrorDialog(project, JavaRefactoringBundle.message("destination.directory.does.not.correspond.to.any.package"),
                               JavaRefactoringBundle.message("cannot.move"));
      return null;
    }

    final JavaRefactoringFactory factory = JavaRefactoringFactory.getInstance(project);
    return myPreserveSourceRoot.isSelected() && myPreserveSourceRoot.isVisible()
           ? factory.createSourceFolderPreservingMoveDestination(aPackage.getQualifiedName())
           : factory.createSourceRootMoveDestination(aPackage.getQualifiedName(), sourceRoot);
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return "MoveClassWithTarget";
  }
}



