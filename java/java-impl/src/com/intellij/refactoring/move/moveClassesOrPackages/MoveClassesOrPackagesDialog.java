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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.*;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.move.MoveDialogBase;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.ui.ClassNameReferenceEditor;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Set;

public class MoveClassesOrPackagesDialog extends MoveDialogBase {
  @NonNls private static final String RECENTS_KEY = "MoveClassesOrPackagesDialog.RECENTS_KEY";
  private final PsiElement[] myElementsToMove;
  private final MoveCallback myMoveCallback;

  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog");


  private JLabel myNameLabel;
  private ReferenceEditorComboWithBrowseButton myWithBrowseButtonReference;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchTextOccurences;
  private String myHelpID;
  private final boolean mySearchTextOccurencesEnabled;
  private final PsiManager myManager;
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
  private JPanel myOpenInEditorPanel;
  private boolean myHavePackages;
  private boolean myTargetDirectoryFixed;
  private boolean mySuggestToMoveToAnotherRoot;

  public MoveClassesOrPackagesDialog(Project project,
                                     boolean searchTextOccurences,
                                     PsiElement[] elementsToMove,
                                     final PsiElement initialTargetElement,
                                     MoveCallback moveCallback) {
    super(project, true);
    myElementsToMove = elementsToMove;
    myMoveCallback = moveCallback;
    myManager = PsiManager.getInstance(myProject);
    setTitle(MoveHandler.REFACTORING_NAME);
    mySearchTextOccurencesEnabled = searchTextOccurences;

    selectInitialCard();

    init();

    if (initialTargetElement instanceof PsiClass) {
      myMakeInnerClassOfRadioButton.setSelected(true);

      myInnerClassChooser.setText(((PsiClass)initialTargetElement).getQualifiedName());

      ApplicationManager.getApplication().invokeLater(() -> myInnerClassChooser.requestFocus(), ModalityState.stateForComponent(myMainPanel));
    }
    else if (initialTargetElement instanceof PsiPackage) {
      myClassPackageChooser.setText(((PsiPackage)initialTargetElement).getQualifiedName());
    }

    updateControlsEnabled();
    myToPackageRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControlsEnabled();
        myClassPackageChooser.requestFocus();
      }
    });
    myMakeInnerClassOfRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControlsEnabled();
        myInnerClassChooser.requestFocus();
      }
    });

    for (PsiElement element : elementsToMove) {
      if (element.getContainingFile() != null) {
        myOpenInEditorPanel.add(initOpenInEditorCb(), BorderLayout.EAST);
        break;
      }
    }
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

  public JComponent getPreferredFocusedComponent() {
    return myHavePackages ? myWithBrowseButtonReference.getChildComponent() : myClassPackageChooser.getChildComponent();
  }

  protected JComponent createCenterPanel() {
    boolean isDestinationVisible = getSourceRoots().size() > 1;
    myDestinationFolderCB.setVisible(isDestinationVisible);
    myTargetDestinationLabel.setVisible(isDestinationVisible);
    return null;
  }

  private void createUIComponents() {
    myMainPanel = new JPanel();

    myWithBrowseButtonReference = createPackageChooser();
    myClassPackageChooser = createPackageChooser();

    GlobalSearchScope scope = JavaProjectRootsUtil.getScopeWithoutGeneratedSources(ProjectScope.getProjectScope(myProject), myProject);
    myInnerClassChooser = new ClassNameReferenceEditor(myProject, null, scope);
    myInnerClassChooser.addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        validateButtons();
      }
    });

    // override CardLayout sizing behavior
    myCardPanel = new JPanel() {
      public Dimension getMinimumSize() {
        return myHavePackages ? myMovePackagePanel.getMinimumSize() : myMoveClassPanel.getMinimumSize();
      }

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
    document.addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        validateButtons();
      }
    });
    
    return packageChooser;
  }

  protected JComponent createNorthPanel() {
    if (!mySearchTextOccurencesEnabled) {
      myCbSearchTextOccurences.setEnabled(false);
      myCbSearchTextOccurences.setVisible(false);
      myCbSearchTextOccurences.setSelected(false);
    }

    return myMainPanel;
  }

  protected String getDimensionServiceKey() {
    return myHavePackages
           ? "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog.packages"
           : "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog.classes";
  }

  public void setData(PsiElement[] psiElements,
                      String targetPackageName,
                      PsiDirectory initialTargetDirectory,
                      boolean isTargetDirectoryFixed,
                      boolean suggestToMoveToAnotherRoot,
                      boolean searchInComments,
                      boolean searchForTextOccurences,
                      String helpID) {
    myTargetDirectoryFixed = isTargetDirectoryFixed;
    mySuggestToMoveToAnotherRoot = suggestToMoveToAnotherRoot;
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
    else if (psiElements.length == 1) {
      PsiElement firstElement = psiElements[0];
      if (firstElement instanceof PsiClass) {
        LOG.assertTrue(!MoveClassesOrPackagesImpl.isClassInnerOrLocal((PsiClass)firstElement));
      }
      else {
        PsiElement parent = firstElement.getParent();
        LOG.assertTrue(parent != null);
      }
      myNameLabel.setText(RefactoringBundle.message("move.single.class.or.package.name.label", UsageViewUtil.getType(firstElement),
                                                    UsageViewUtil.getLongName(firstElement)));
    }
    else if (psiElements.length > 1) {
      myNameLabel.setText(psiElements[0] instanceof PsiClass
                          ? RefactoringBundle.message("move.specified.classes")
                          : RefactoringBundle.message("move.specified.packages"));
    }
    selectInitialCard();

    myCbSearchInComments.setSelected(searchInComments);
    myCbSearchTextOccurences.setSelected(searchForTextOccurences);

    if (initialTargetDirectory != null && 
        JavaMoveClassesOrPackagesHandler.packageHasMultipleDirectoriesInModule(myProject, initialTargetDirectory)) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final Set<VirtualFile> initialRoots = new HashSet<>();
      collectSourceRoots(psiElements, fileIndex, initialRoots);
      if (initialRoots.size() > 1) {
        initialTargetDirectory = null;
      }
    }
    ((DestinationFolderComboBox)myDestinationFolderCB).setData(myProject, initialTargetDirectory,
                                                               new Pass<String>() {
                                                                 @Override
                                                                 public void pass(String s) {
                                                                   setErrorText(s);
                                                                 }
                                                               }, myHavePackages ? myWithBrowseButtonReference.getChildComponent() : myClassPackageChooser.getChildComponent());
    UIUtil.setEnabled(myTargetPanel, !getSourceRoots().isEmpty() && isMoveToPackage() && !isTargetDirectoryFixed, true);
    validateButtons();
    myHelpID = helpID;
  }

  private static void collectSourceRoots(PsiElement[] psiElements, ProjectFileIndex fileIndex, Set<VirtualFile> initialRoots) {
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

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  protected final boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (isMoveToPackage()) {
      String name = getTargetPackage().trim();
      if (name.length() != 0 && !PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(name)) {
        throw new ConfigurationException("\'" + name + "\' is invalid destination package name");
      }
    }
    else {
      if (findTargetClass() == null) throw new ConfigurationException("Destination class not found");
      final String validationError = verifyInnerClassDestination();
      if (validationError != null) throw new ConfigurationException(validationError);
    }
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
  private static String verifyDestinationForElement(final PsiElement element, final MoveDestination moveDestination) {
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

  protected void doAction() {
    saveOpenInEditorOption();
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
          /*PsiElement toAdd;
          if (aClass.getContainingFile() instanceof PsiJavaFile && ((PsiJavaFile)aClass.getContainingFile()).getClasses().length > 1) {
            toAdd = aClass;
          }
          else {
            toAdd = aClass.getContainingFile();
          }*/

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
  protected MoveClassesOrPackagesProcessor createMoveToPackageProcessor(MoveDestination destination, final PsiElement[] elementsToMove,
                                                                        final MoveCallback callback) {
    return new MoveClassesOrPackagesProcessor(getProject(), elementsToMove, destination, isSearchInComments(), isSearchInNonJavaFiles(), callback);
  }

  private void saveRefactoringSettings() {
    final JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
    final boolean searchInComments = isSearchInComments();
    final boolean searchForTextOccurences = isSearchInNonJavaFiles();
    refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
    refactoringSettings.MOVE_SEARCH_FOR_TEXT = searchForTextOccurences;
    refactoringSettings.MOVE_PREVIEW_USAGES = isPreviewUsages();
  }

  @Nullable
  private String verifyInnerClassDestination() {
    PsiClass targetClass = findTargetClass();
    if (targetClass == null) return null;

    for (PsiElement element : myElementsToMove) {
      if (PsiTreeUtil.isAncestor(element, targetClass, false)) {
        return RefactoringBundle.message("move.class.to.inner.move.to.self.error");
      }
      final Language targetClassLanguage = targetClass.getLanguage();
      if (!element.getLanguage().equals(targetClassLanguage)) {
        return RefactoringBundle.message("move.to.different.language", UsageViewUtil.getType(element),
                                         ((PsiClass)element).getQualifiedName(), targetClass.getQualifiedName());
      }
    }

    while (targetClass != null) {
      if (targetClass.getContainingClass() != null && !targetClass.hasModifierProperty(PsiModifier.STATIC)) {
        return RefactoringBundle.message("move.class.to.inner.nonstatic.error");
      }
      targetClass = targetClass.getContainingClass();
    }

    return null;
  }

  private void invokeMoveToInner() {
    saveRefactoringSettings();
    final PsiClass targetClass = findTargetClass();
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
  protected MoveClassToInnerProcessor createMoveToInnerProcessor(@NotNull PsiClass destination, @NotNull PsiClass[] classesToMove, @Nullable final MoveCallback callback) {
    return new MoveClassToInnerProcessor(getProject(), classesToMove, destination, isSearchInComments(), isSearchInNonJavaFiles(), callback);
  }

  protected final boolean isSearchInNonJavaFiles() {
    return myCbSearchTextOccurences.isSelected();
  }

  @Nullable
  private MoveDestination selectDestination() {
    final String packageName = getTargetPackage().trim();
    if (packageName.length() > 0 && !PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(packageName)) {
      Messages.showErrorDialog(myProject, RefactoringBundle.message("please.enter.a.valid.target.package.name"),
                               RefactoringBundle.message("move.title"));
      return null;
    }
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    PackageWrapper targetPackage = new PackageWrapper(myManager, packageName);
    if (!targetPackage.exists()) {
      final int ret = Messages.showYesNoDialog(myProject, RefactoringBundle.message("package.does.not.exist", packageName),
                                               RefactoringBundle.message("move.title"), Messages.getQuestionIcon());
      if (ret != Messages.YES) return null;
    }

    return ((DestinationFolderComboBox)myDestinationFolderCB).selectDirectory(targetPackage, mySuggestToMoveToAnotherRoot);
  }

  private List<VirtualFile> getSourceRoots() {
    return JavaProjectRootsUtil.getSuitableDestinationSourceRoots(myProject);
  }

  @Override
  protected String getMovePropertySuffix() {
    return "Class";
  }

  @Override
  protected String getCbTitle() {
    return "Open moved in editor";
  }

  @Override
  protected boolean isEnabledByDefault() {
    return false;
  }
}
