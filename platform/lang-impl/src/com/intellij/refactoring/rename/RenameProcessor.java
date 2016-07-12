/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.refactoring.rename;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RelatedUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.util.*;

public class RenameProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameProcessor");

  protected final LinkedHashMap<PsiElement, String> myAllRenames = new LinkedHashMap<PsiElement, String>();

  private @NotNull PsiElement myPrimaryElement;
  private String myNewName = null;

  private boolean mySearchInComments;
  private boolean mySearchTextOccurrences;
  protected boolean myForceShowPreview;

  private String myCommandName;

  private NonCodeUsageInfo[] myNonCodeUsages = new NonCodeUsageInfo[0];
  private final List<AutomaticRenamerFactory> myRenamerFactories = new ArrayList<AutomaticRenamerFactory>();
  private final List<AutomaticRenamer> myRenamers = new ArrayList<AutomaticRenamer>();
  private final List<UnresolvableCollisionUsageInfo> mySkippedUsages = new ArrayList<UnresolvableCollisionUsageInfo>();

  public RenameProcessor(Project project,
                         @NotNull PsiElement element,
                         @NotNull @NonNls String newName,
                         boolean isSearchInComments,
                         boolean isSearchTextOccurrences) {
    super(project);
    myPrimaryElement = element;

    assertNonCompileElement(element);
    //assertValidName(element, newName);

    mySearchInComments = isSearchInComments;
    mySearchTextOccurrences = isSearchTextOccurrences;

    setNewName(newName);
  }

  public Set<PsiElement> getElements() {
    return Collections.unmodifiableSet(myAllRenames.keySet());
  }

  public String getNewName(PsiElement element) {
    return myAllRenames.get(element);
  }

  public void addRenamerFactory(AutomaticRenamerFactory factory) {
    if (!myRenamerFactories.contains(factory)) {
      myRenamerFactories.add(factory);
    }
  }

  public void removeRenamerFactory(AutomaticRenamerFactory factory) {
    myRenamerFactories.remove(factory);
  }

  @Override
  public void doRun() {
    if (!myPrimaryElement.isValid()) return;
    prepareRenaming(myPrimaryElement, myNewName, myAllRenames);

    super.doRun();
  }

  public void prepareRenaming(@NotNull final PsiElement element, final String newName, final LinkedHashMap<PsiElement, String> allRenames) {
    final List<RenamePsiElementProcessor> processors = RenamePsiElementProcessor.allForElement(element);
    myForceShowPreview = false;
    for (RenamePsiElementProcessor processor : processors) {
      if (processor.canProcessElement(element)) {
        processor.prepareRenaming(element, newName, allRenames);
        myForceShowPreview |= processor.forcesShowPreview();
      }
    }
  }

  @Nullable
  private String getHelpID() {
    return RenamePsiElementProcessor.forElement(myPrimaryElement).getHelpID(myPrimaryElement);
  }

  @Override
  public boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    RenameUtil.addConflictDescriptions(usagesIn, conflicts);
    RenamePsiElementProcessor.forElement(myPrimaryElement).findExistingNameConflicts(myPrimaryElement, myNewName, conflicts, myAllRenames);
    if (!conflicts.isEmpty()) {

      final RefactoringEventData conflictData = new RefactoringEventData();
      conflictData.putUserData(RefactoringEventData.CONFLICTS_KEY, conflicts.values());
      myProject.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .conflictsDetected("refactoring.rename", conflictData);

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new ConflictsInTestsException(conflicts.values());
      }
      ConflictsDialog conflictsDialog = prepareConflictsDialog(conflicts, refUsages.get());
      if (!conflictsDialog.showAndGet()) {
        if (conflictsDialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }

    final List<UsageInfo> variableUsages = new ArrayList<UsageInfo>();
    if (!myRenamers.isEmpty()) {
      if (!findRenamedVariables(variableUsages)) return false;
      final LinkedHashMap<PsiElement, String> renames = new LinkedHashMap<PsiElement, String>();
      for (final AutomaticRenamer renamer : myRenamers) {
        final List<? extends PsiNamedElement> variables = renamer.getElements();
        for (final PsiNamedElement variable : variables) {
          final String newName = renamer.getNewName(variable);
          if (newName != null) {
            addElement(variable, newName);
            prepareRenaming(variable, newName, renames);
          }
        }
      }
      if (!renames.isEmpty()) {
        for (PsiElement element : renames.keySet()) {
          assertNonCompileElement(element);
        }
        myAllRenames.putAll(renames);
        final Runnable runnable = () -> {
          for (final Map.Entry<PsiElement, String> entry : renames.entrySet()) {
            final UsageInfo[] usages =
              ApplicationManager.getApplication().runReadAction(new Computable<UsageInfo[]>() {
                @Override
                public UsageInfo[] compute() {
                  return RenameUtil.findUsages(entry.getKey(), entry.getValue(), mySearchInComments, mySearchTextOccurrences, myAllRenames);
                }
              });
            Collections.addAll(variableUsages, usages);
          }
        };
        if (!ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject)) {
          return false;
        }
      }
    }

    final Set<UsageInfo> usagesSet = ContainerUtil.newLinkedHashSet(usagesIn);
    usagesSet.addAll(variableUsages);
    final List<UnresolvableCollisionUsageInfo> conflictUsages = RenameUtil.removeConflictUsages(usagesSet);
    if (conflictUsages != null) {
      mySkippedUsages.addAll(conflictUsages);
    }
    refUsages.set(usagesSet.toArray(new UsageInfo[usagesSet.size()]));

    prepareSuccessful();
    return PsiElementRenameHandler.canRename(myProject, null, myPrimaryElement);
  }

  public static void assertNonCompileElement(PsiElement element) {
    LOG.assertTrue(!(element instanceof PsiCompiledElement), element);
  }
  
  private void assertValidName(PsiElement element, String newName) {
    LOG.assertTrue(RenameUtil.isValidName(myProject, element, newName), "element: " + element + ", newName: " + newName);
  }

  private boolean findRenamedVariables(final List<UsageInfo> variableUsages) {
    for (Iterator<AutomaticRenamer> iterator = myRenamers.iterator(); iterator.hasNext(); ) {
      AutomaticRenamer automaticVariableRenamer = iterator.next();
      if (!automaticVariableRenamer.hasAnythingToRename()) continue;
      if (!showAutomaticRenamingDialog(automaticVariableRenamer)) {
        iterator.remove();
      }
    }

    final Runnable runnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
      for (final AutomaticRenamer renamer : myRenamers) {
        renamer.findUsages(variableUsages, mySearchInComments, mySearchTextOccurrences, mySkippedUsages, myAllRenames);
      }
    });

    return ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject);
  }

  protected boolean showAutomaticRenamingDialog(AutomaticRenamer automaticVariableRenamer) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (PsiNamedElement element : automaticVariableRenamer.getElements()) {
        automaticVariableRenamer.setRename(element, automaticVariableRenamer.getNewName(element));
      }
      return true;
    }
    final AutomaticRenamingDialog dialog = new AutomaticRenamingDialog(myProject, automaticVariableRenamer);
    return dialog.showAndGet();
  }

  public void addElement(@NotNull PsiElement element, @NotNull String newName) {
    assertNonCompileElement(element);
    myAllRenames.put(element, newName);
  }

  private void setNewName(@NotNull String newName) {
    myNewName = newName;
    myAllRenames.put(myPrimaryElement, newName);
    myCommandName = RefactoringBundle
      .message("renaming.0.1.to.2", UsageViewUtil.getType(myPrimaryElement), DescriptiveNameUtil.getDescriptiveName(myPrimaryElement), newName);
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new RenameViewDescriptor(myAllRenames);
  }

  @Override
  @NotNull
  public UsageInfo[] findUsages() {
    myRenamers.clear();
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    List<PsiElement> elements = new ArrayList<PsiElement>(myAllRenames.keySet());
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);
      if (element == null) {
        LOG.error("primary: " + myPrimaryElement + "; renamers: " + myRenamers);
        continue;
      }
      final String newName = myAllRenames.get(element);
      final UsageInfo[] usages = RenameUtil.findUsages(element, newName, mySearchInComments, mySearchTextOccurrences, myAllRenames);
      final List<UsageInfo> usagesList = Arrays.asList(usages);
      result.addAll(usagesList);

      for (AutomaticRenamerFactory factory : myRenamerFactories) {
        if (factory.isApplicable(element)) {
          myRenamers.add(factory.createRenamer(element, newName, usagesList));
        }
      }

      for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
        if (factory.getOptionName() == null && factory.isApplicable(element)) {
          myRenamers.add(factory.createRenamer(element, newName, usagesList));
        }
      }
    }
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos);
    return usageInfos;
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    LOG.assertTrue(elements.length > 0);
    myPrimaryElement = elements[0];

    final Iterator<String> newNames = myAllRenames.values().iterator();
    LinkedHashMap<PsiElement, String> newAllRenames = new LinkedHashMap<PsiElement, String>();
    for (PsiElement resolved : elements) {
      newAllRenames.put(resolved, newNames.next());
    }
    myAllRenames.clear();
    myAllRenames.putAll(newAllRenames);
  }

  @Override
  protected boolean isPreviewUsages(@NotNull UsageInfo[] usages) {
    if (myForceShowPreview) return true;
    if (super.isPreviewUsages(usages)) return true;
    if (UsageViewUtil.reportNonRegularUsages(usages, myProject)) return true;
    return false;
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.rename";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(myPrimaryElement);
    return data;
  }

  @Nullable
  @Override
  protected RefactoringEventData getAfterData(@NotNull UsageInfo[] usages) {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(myPrimaryElement);
    return data;
  }

  @Override
  public void performRefactoring(@NotNull UsageInfo[] usages) {
    final int[] choice = myAllRenames.size() > 1 ? new int[]{-1} : null;
    String message = null;
    try {
      for (Iterator<Map.Entry<PsiElement, String>> iterator = myAllRenames.entrySet().iterator(); iterator.hasNext(); ) {
        Map.Entry<PsiElement, String> entry = iterator.next();
        if (entry.getKey() instanceof PsiFile) {
          final PsiFile file = (PsiFile)entry.getKey();
          final PsiDirectory containingDirectory = file.getContainingDirectory();
          if (CopyFilesOrDirectoriesHandler.checkFileExist(containingDirectory, choice, file, entry.getValue(), "Rename")) {
            iterator.remove();
            continue;
          }
        }
        RenameUtil.checkRename(entry.getKey(), entry.getValue());
      }
    }
    catch (IncorrectOperationException e) {
      message = e.getMessage();
    }

    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("rename.title"), message, getHelpID(), myProject);
      return;
    }

    List<Runnable> postRenameCallbacks = new ArrayList<Runnable>();

    final MultiMap<PsiElement, UsageInfo> classified = classifyUsages(myAllRenames.keySet(), usages);
    for (final PsiElement element : myAllRenames.keySet()) {
      String newName = myAllRenames.get(element);

      final RefactoringElementListener elementListener = getTransaction().getElementListener(element);
      final RenamePsiElementProcessor renamePsiElementProcessor = RenamePsiElementProcessor.forElement(element);
      Runnable postRenameCallback = renamePsiElementProcessor.getPostRenameCallback(element, newName, elementListener);
      final Collection<UsageInfo> infos = classified.get(element);
      try {
        RenameUtil.doRename(element, newName, infos.toArray(new UsageInfo[infos.size()]), myProject, elementListener);
      }
      catch (final IncorrectOperationException e) {
        RenameUtil.showErrorMessage(e, element, myProject);
        return;
      }
      if (postRenameCallback != null) {
        postRenameCallbacks.add(postRenameCallback);
      }
    }

    for (Runnable runnable : postRenameCallbacks) {
      runnable.run();
    }

    List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<NonCodeUsageInfo>();
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) {
        nonCodeUsages.add((NonCodeUsageInfo)usage);
      }
    }
    myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[nonCodeUsages.size()]);
    if (!mySkippedUsages.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
        ApplicationManager.getApplication().invokeLater(() -> {
          final IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
          if (ideFrame != null) {

            StatusBarEx statusBar = (StatusBarEx)ideFrame.getStatusBar();
            HyperlinkListener listener = new HyperlinkListener() {
              @Override
              public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
                Messages.showMessageDialog("<html>Following usages were safely skipped:<br>" +
                                           StringUtil.join(mySkippedUsages, unresolvableCollisionUsageInfo -> unresolvableCollisionUsageInfo.getDescription(), "<br>") +
                                           "</html>", "Not All Usages Were Renamed", null);
              }
            };
            statusBar.notifyProgressByBalloon(MessageType.WARNING, "<html><body>Unable to rename certain usages. <a href=\"\">Browse</a></body></html>", null, listener);
          }
        }, ModalityState.NON_MODAL);
      }
    }
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
  }

  @Override
  protected String getCommandName() {
    return myCommandName;
  }

  public static MultiMap<PsiElement, UsageInfo> classifyUsages(Collection<? extends PsiElement> elements, UsageInfo[] usages) {
    final MultiMap<PsiElement, UsageInfo> result = new MultiMap<PsiElement, UsageInfo>();
    for (UsageInfo usage : usages) {
      LOG.assertTrue(usage instanceof MoveRenameUsageInfo);
      if (usage.getReference() instanceof LightElement) {
        continue; //filter out implicit references (e.g. from derived class to super class' default constructor)
      }
      MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo)usage;
      if (usage instanceof RelatedUsageInfo) {
        final PsiElement relatedElement = ((RelatedUsageInfo)usage).getRelatedElement();
        if (elements.contains(relatedElement)) {
          result.putValue(relatedElement, usage);
        }
      } else {
        PsiElement referenced = usageInfo.getReferencedElement();
        if (elements.contains(referenced)) {
          result.putValue(referenced, usage);
        } else if (referenced != null) {
          PsiElement indirect = referenced.getNavigationElement();
          if (elements.contains(indirect)) {
            result.putValue(indirect, usage);
          }
        }

      }
    }
    return result;
  }

  public Collection<String> getNewNames() {
    return myAllRenames.values();
  }

  public void setSearchInComments(boolean value) {
    mySearchInComments = value;
  }

  public void setSearchTextOccurrences(boolean searchTextOccurrences) {
    mySearchTextOccurrences = searchTextOccurrences;
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public boolean isSearchTextOccurrences() {
    return mySearchTextOccurrences;
  }

  public void setCommandName(final String commandName) {
    myCommandName = commandName;
  }
}
