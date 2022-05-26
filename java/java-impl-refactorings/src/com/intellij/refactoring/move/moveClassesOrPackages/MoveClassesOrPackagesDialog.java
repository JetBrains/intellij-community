// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.CommonBundle;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.*;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.ui.ClassNameReferenceEditor;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class MoveClassesOrPackagesDialog extends MoveDialogBase {
  private static final String RECENTS_KEY = "MoveClassesOrPackagesDialog.RECENTS_KEY";
  private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesDialog.class);

  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;
  private final PsiManager myManager;
  private final boolean mySearchTextOccurrencesEnabled;
  private final boolean myTargetDirectoryFixed;
  private final boolean mySuggestToMoveToAnotherRoot;
  private final String myHelpID;

  private JLabel myNameLabel;
  private ReferenceEditorComboWithBrowseButton myWithBrowseButtonReference;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurrences;
  private JPanel myMainPanel;
  private JRadioButton myToPackageRadioButton;
  private JRadioButton myMakeInnerClassOfRadioButton;
  private ReferenceEditorComboWithBrowseButton myClassPackageChooser;
  private JPanel myCardPanel;
  private ReferenceEditorWithBrowseButton myInnerClassChooser;
  private JPanel myMoveClassPanel;
  private JPanel myMovePackagePanel;
  private ComboboxWithBrowseButton myDestinationFolderCB;
  private JPanel myTargetPanel;
  private JLabel myTargetDestinationLabel;
  private boolean myHavePackages;

  public MoveClassesOrPackagesDialog(@NotNull Project project,
                                     boolean searchTextOccurrences,
                                     PsiElement @NotNull [] elementsToMove,
                                     PsiElement initialTargetElement,
                                     MoveCallback moveCallback,
                                     @NotNull String targetPackageName,
                                     PsiDirectory initialTargetDirectory,
                                     boolean searchInComments,
                                     boolean searchForTextOccurrences) {
    super(project, true, canBeOpenedInEditor(elementsToMove));

    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    myManager = PsiManager.getInstance(myProject);
    setTitle(MoveHandler.getRefactoringName());
    mySearchTextOccurrencesEnabled = searchTextOccurrences;

    selectInitialCard();

    init();

    if (initialTargetElement instanceof PsiClass) {
      myMakeInnerClassOfRadioButton.setSelected(true);

      String qualifiedName = ((PsiClass)initialTargetElement).getQualifiedName();
      assert qualifiedName != null : initialTargetElement;
      myInnerClassChooser.setText(qualifiedName);

      ApplicationManager.getApplication().invokeLater(
        () -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
          () -> IdeFocusManager.getGlobalInstance().requestFocus(myInnerClassChooser, true)),
        ModalityState.stateForComponent(myMainPanel));
    }
    else if (initialTargetElement instanceof PsiPackage) {
      myClassPackageChooser.setText(((PsiPackage)initialTargetElement).getQualifiedName());
    }

    updateControlsEnabled();
    myToPackageRadioButton.addActionListener(e -> {
      updateControlsEnabled();
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myClassPackageChooser, true));
    });
    myMakeInnerClassOfRadioButton.addActionListener(e -> {
      updateControlsEnabled();
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myInnerClassChooser, true));
    });

    myTargetDirectoryFixed = initialTargetDirectory == null;
    mySuggestToMoveToAnotherRoot = initialTargetElement == null;
    if (targetPackageName.length() != 0) {
      myWithBrowseButtonReference.prependItem(targetPackageName);
      myClassPackageChooser.prependItem(targetPackageName);
    }

    String nameFromCallback = myMoveCallback instanceof MoveClassesOrPackagesCallback
                              ? ((MoveClassesOrPackagesCallback)myMoveCallback).getElementsToMoveName()
                              : null;
    if (nameFromCallback != null) {
      myNameLabel.setText(nameFromCallback);
    }
    else if (elementsToMove.length == 1) {
      PsiElement firstElement = elementsToMove[0];
      if (firstElement instanceof PsiClass) {
        LOG.assertTrue(!MoveClassesOrPackagesImpl.isClassInnerOrLocal((PsiClass)firstElement));
      }
      else {
        PsiElement parent = firstElement.getParent();
        LOG.assertTrue(parent != null);
      }
      myNameLabel.setText(JavaRefactoringBundle.message("move.single.class.or.package.name.label", UsageViewUtil.getType(firstElement), UsageViewUtil.getLongName(firstElement)));
    }
    else if (elementsToMove.length > 1) {
      myNameLabel.setText(JavaRefactoringBundle.message(elementsToMove[0] instanceof PsiClass ? "move.specified.classes" : "move.specified.packages"));
    }
    selectInitialCard();

    myCbSearchInComments.setSelected(searchInComments);
    myCbSearchTextOccurrences.setSelected(searchForTextOccurrences);

    if (initialTargetDirectory != null &&
        JavaMoveClassesOrPackagesHandler.packageHasMultipleDirectoriesInModule(myProject, initialTargetDirectory)) {
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      Set<VirtualFile> initialRoots = new HashSet<>();
      collectSourceRoots(elementsToMove, fileIndex, initialRoots);
      if (initialRoots.size() > 1) {
        initialTargetDirectory = null;
      }
    }
    Consumer<String> updater = (@NlsContexts.DialogMessage var s) -> {
      setErrorText(s, myDestinationFolderCB);
      if (s == null) {
        validateButtons();
      }
    };
    validateButtons();

    EditorComboBox comboBox = myHavePackages ? myWithBrowseButtonReference.getChildComponent() : myClassPackageChooser.getChildComponent();
    ((DestinationFolderComboBox)myDestinationFolderCB).setData(myProject, initialTargetDirectory, updater, comboBox);
    UIUtil.setEnabled(myTargetPanel, !getSourceRoots().isEmpty() && isMoveToPackage() && !myTargetDirectoryFixed, true);

    myHelpID = HelpID.getMoveHelpID(elementsToMove[0]);
  }

  static boolean canBeOpenedInEditor(PsiElement @NotNull [] elementsToMove) {
    for (PsiElement element : elementsToMove) {
      if (element.getContainingFile() != null) {
        return true;
      }
    }
    return false;
  }

  private void updateControlsEnabled() {
    myClassPackageChooser.setEnabled(myToPackageRadioButton.isSelected());
    myInnerClassChooser.setEnabled(myMakeInnerClassOfRadioButton.isSelected());
    UIUtil.setEnabled(myTargetPanel, isMoveToPackage() && getSourceRoots().size() > 1 && !myTargetDirectoryFixed, true);
    validateButtons();
  }

  private void selectInitialCard() {
    myHavePackages = false;
    for (PsiElement psiElement : myElementsToMove) {
      if (!(psiElement instanceof PsiClass)) {
        myHavePackages = true;
        break;
      }
    }
    CardLayout cardLayout = (CardLayout)myCardPanel.getLayout();
    cardLayout.show(myCardPanel, myHavePackages ? "Package" : "Class");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myHavePackages ? myWithBrowseButtonReference.getChildComponent() : myClassPackageChooser.getChildComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    boolean isDestinationVisible = getSourceRoots().size() > 1;
    myDestinationFolderCB.setVisible(isDestinationVisible);
    myTargetDestinationLabel.setVisible(isDestinationVisible);
    return null;
  }

  private void createUIComponents() {
    myWithBrowseButtonReference = createPackageChooser();
    myClassPackageChooser = createPackageChooser();

    GlobalSearchScope scope = JavaProjectRootsUtil.getScopeWithoutGeneratedSources(ProjectScope.getProjectScope(myProject), myProject);
    myInnerClassChooser = new ClassNameReferenceEditor(myProject, null, scope);
    myInnerClassChooser.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        validateButtons();
      }
    });

    // override CardLayout sizing behavior
    myCardPanel = new JPanel() {
      @Override
      public Dimension getMinimumSize() {
        return myHavePackages ? myMovePackagePanel.getMinimumSize() : myMoveClassPanel.getMinimumSize();
      }

      @Override
      public Dimension getPreferredSize() {
        return myHavePackages ? myMovePackagePanel.getPreferredSize() : myMoveClassPanel.getPreferredSize();
      }
    };

    myDestinationFolderCB = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return MoveClassesOrPackagesDialog.this.getTargetPackage();
      }
    };
  }

  private ReferenceEditorComboWithBrowseButton createPackageChooser() {
    final ReferenceEditorComboWithBrowseButton packageChooser =
      new PackageNameReferenceEditorCombo("", myProject, RECENTS_KEY, RefactoringBundle.message("choose.destination.package"));
    final Document document = packageChooser.getChildComponent().getDocument();
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        validateButtons();
      }
    });

    return packageChooser;
  }

  @Override
  protected JComponent createNorthPanel() {
    if (!mySearchTextOccurrencesEnabled) {
      myCbSearchTextOccurrences.setEnabled(false);
      myCbSearchTextOccurrences.setVisible(false);
      myCbSearchTextOccurrences.setSelected(false);
    }

    return myMainPanel;
  }

  @Override
  protected String getHelpId() {
    return myHelpID;
  }

  @Override
  protected String getDimensionServiceKey() {
    return myHavePackages
           ? "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog.packages"
           : "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog.classes";
  }

  private static void collectSourceRoots(PsiElement[] psiElements, ProjectFileIndex fileIndex, Set<? super VirtualFile> initialRoots) {
    for (PsiElement element : psiElements) {
      final VirtualFile file = PsiUtilCore.getVirtualFile(element);
      if (file != null) {
        final VirtualFile sourceRootForFile = fileIndex.getSourceRootForFile(file);
        if (sourceRootForFile != null) {
          initialRoots.add(sourceRootForFile);
        }
      } else if (element instanceof PsiDirectoryContainer) {
        collectSourceRoots(((PsiDirectoryContainer)element).getDirectories(), fileIndex, initialRoots);
      }
    }
  }

  protected final boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (isMoveToPackage()) {
      String name = getTargetPackage().trim();
      if (name.length() != 0 && !PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(name)) {
        throw new ConfigurationException(JavaBundle.message("move.classes.invalid.destination.package.name.message", name));
      }
    }
    else {
      if (findTargetClass() == null) throw new ConfigurationException(
        JavaBundle.message("move.classes.destination.class.not.found.message"));
      final String validationError = verifyInnerClassDestination();
      if (validationError != null) throw new ConfigurationException(validationError);
    }
  }

  @Override
  protected void validateButtons() {
    validateButtonsAsync();
  }

  @Nullable
  private PsiClass findTargetClass() {
    String name = myInnerClassChooser.getText().trim();
    return JavaPsiFacade.getInstance(myManager.getProject()).findClass(name, ProjectScope.getProjectScope(myProject));
  }

  protected boolean isMoveToPackage() {
    return myHavePackages || myToPackageRadioButton.isSelected();
  }

  protected String getTargetPackage() {
    return myHavePackages ? myWithBrowseButtonReference.getText() : myClassPackageChooser.getText();
  }

  @Nullable
  private static @NlsContexts.DialogMessage String verifyDestinationForElement(@NotNull PsiElement element, @NotNull MoveDestination moveDestination) {
    final String message;
    if (element instanceof PsiDirectory) {
      message = moveDestination.verify((PsiDirectory)element);
    }
    else if (element instanceof PsiPackage) {
      message = moveDestination.verify((PsiPackage)element);
    }
    else {
      message = moveDestination.verify(element.getContainingFile());
    }
    return message;
  }

  @Override
  protected void doAction() {
    if (isMoveToPackage()) {
      invokeMoveToPackage();
    }
    else {
      invokeMoveToInner();
    }
  }

  private void invokeMoveToPackage() {
    final MoveDestination destination = selectDestination();
    if (destination == null) return;

    saveRefactoringSettings();
    for (final PsiElement element : myElementsToMove) {
      String message = verifyDestinationForElement(element, destination);
      if (message != null) {
        String helpId = HelpID.getMoveHelpID(myElementsToMove[0]);
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), message, helpId, getProject());
        return;
      }
    }
    try {
      for (PsiElement element : myElementsToMove) {
        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          LOG.assertTrue(aClass.isPhysical(), aClass);
          final PsiDirectory targetDirectory = destination.getTargetIfExists(element.getContainingFile());
          if (targetDirectory != null) {
            MoveFilesOrDirectoriesUtil.checkMove(aClass, targetDirectory);
          }
        }
      }

      MoveClassesOrPackagesProcessor processor = createMoveToPackageProcessor(destination, myElementsToMove, myMoveCallback);
      if (processor.verifyValidPackageName()) {
        processor.setOpenInEditor(isOpenInEditor());
        invokeRefactoring(processor);
      }
    }
    catch (IncorrectOperationException e) {
      String helpId = HelpID.getMoveHelpID(myElementsToMove[0]);
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), helpId, getProject());
    }
  }

  //for scala plugin
  protected MoveClassesOrPackagesProcessor createMoveToPackageProcessor(@NotNull MoveDestination destination,
                                                                        PsiElement @NotNull [] elementsToMove,
                                                                        MoveCallback callback) {
    return new MoveClassesOrPackagesProcessor(getProject(), elementsToMove, destination, isSearchInComments(), isSearchInNonJavaFiles(), callback);
  }

  private void saveRefactoringSettings() {
    JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = isSearchInComments();
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = isSearchInNonJavaFiles();
  }

  @Nullable
  private @NlsContexts.DialogMessage String verifyInnerClassDestination() {
    PsiClass targetClass = findTargetClass();
    if (targetClass == null) return null;

    for (PsiElement element : myElementsToMove) {
      if (PsiTreeUtil.isAncestor(element, targetClass, false)) {
        return JavaRefactoringBundle.message("move.class.to.inner.move.to.self.error");
      }
      final Language targetClassLanguage = targetClass.getLanguage();
      if (!element.getLanguage().equals(targetClassLanguage)) {
        return RefactoringBundle.message("move.to.different.language", UsageViewUtil.getType(element),
                                         ((PsiClass)element).getQualifiedName(), targetClass.getQualifiedName());
      }
    }

    while (targetClass != null) {
      if (targetClass.getContainingClass() != null && !targetClass.hasModifierProperty(PsiModifier.STATIC)) {
        return JavaRefactoringBundle.message("move.class.to.inner.nonstatic.error");
      }
      targetClass = targetClass.getContainingClass();
    }

    return null;
  }

  private void invokeMoveToInner() {
    saveRefactoringSettings();
    final String message = JavaRefactoringBundle.message("move.class.to.inner.find.target.class.progress");
    final PsiClass targetClass = ProgressManager.getInstance().
      runProcessWithProgressSynchronously(() -> ReadAction.compute(() -> findTargetClass()), message, true, myProject);
    if (targetClass == null) return;
    final PsiClass[] classesToMove = new PsiClass[myElementsToMove.length];
    for (int i = 0; i < myElementsToMove.length; i++) {
      classesToMove[i] = (PsiClass)myElementsToMove[i];
    }
    final MoveClassToInnerProcessor processor = createMoveToInnerProcessor(targetClass, classesToMove, myMoveCallback);
    processor.setOpenInEditor(isOpenInEditor());
    invokeRefactoring(processor);
  }

  //for scala plugin
  protected MoveClassToInnerProcessor createMoveToInnerProcessor(@NotNull PsiClass destination,
                                                                 PsiClass @NotNull [] classesToMove,
                                                                 @Nullable MoveCallback callback) {
    return new MoveClassToInnerProcessor(getProject(), classesToMove, destination, isSearchInComments(), isSearchInNonJavaFiles(), callback);
  }

  protected final boolean isSearchInNonJavaFiles() {
    return myCbSearchTextOccurrences.isSelected();
  }

  @Nullable
  private MoveDestination selectDestination() {
    final String packageName = getTargetPackage().trim();
    if (packageName.length() > 0 && !PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(packageName)) {
      Messages.showErrorDialog(myProject, JavaRefactoringBundle.message("please.enter.a.valid.target.package.name"),
                               RefactoringBundle.message("move.title"));
      return null;
    }
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    PackageWrapper targetPackage = new PackageWrapper(myManager, packageName);
    if (!targetPackage.exists()) {
      if (isPreviewUsages()) {
        int res = Messages.showOkCancelDialog(myProject, 
                                              JavaRefactoringBundle.message("package.does.not.exist.preview", packageName),
                                              RefactoringBundle.message("move.title"), 
                                              CommonBundle.getOkButtonText(),
                                              CommonBundle.getCancelButtonText(), null);
        if (res != Messages.OK) return null;
      }
      else {
        final int ret = Messages.showYesNoDialog(myProject, JavaRefactoringBundle.message("package.does.not.exist", packageName),
                                                 RefactoringBundle.message("move.title"), Messages.getQuestionIcon());
        if (ret != Messages.YES) return null;
      }
    }

    return ((DestinationFolderComboBox)myDestinationFolderCB).selectDirectory(targetPackage, mySuggestToMoveToAnotherRoot);
  }

  private List<VirtualFile> getSourceRoots() {
    return JavaProjectRootsUtil.getSuitableDestinationSourceRoots(myProject);
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return "MoveClass";
  }
}