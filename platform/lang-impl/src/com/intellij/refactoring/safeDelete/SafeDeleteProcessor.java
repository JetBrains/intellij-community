// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.lang.LangBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NlsContexts.Command;
import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public final class SafeDeleteProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(SafeDeleteProcessor.class);
  private final PsiElement @NotNull [] myElements;
  private boolean mySearchInCommentsAndStrings;
  private boolean mySearchNonJava;
  private boolean myPreviewNonCodeUsages = true;
  private Runnable myAfterRefactoringCallback;

  private SafeDeleteProcessor(@NotNull Project project, @Nullable Runnable prepareSuccessfulCallback,
                              PsiElement @NotNull [] elementsToDelete, boolean isSearchInComments, boolean isSearchNonJava) {
    super(project, prepareSuccessfulCallback);
    myElements = elementsToDelete;
    mySearchInCommentsAndStrings = isSearchInComments;
    mySearchNonJava = isSearchNonJava;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new SafeDeleteUsageViewDescriptor(myElements);
  }

  private static boolean isInside(PsiElement place, PsiElement[] ancestors) {
    return isInside(place, Arrays.asList(ancestors));
  }

  private static boolean isInside(PsiElement place, Collection<? extends PsiElement> ancestors) {
    for (PsiElement element : ancestors) {
      if (isInside(place, element)) return true;
    }
    return false;
  }

  public static boolean isInside(@NotNull PsiElement place, PsiElement ancestor) {
    if (ancestor instanceof PsiDirectoryContainer container) {
      for (PsiDirectory directory : container.getDirectories(place.getResolveScope())) {
        if (isInside(place, directory)) return true;
      }
    }

    if (ancestor instanceof PsiFile f) {
      for (PsiFile file : f.getViewProvider().getAllFiles()) {
        if (PsiTreeUtil.isAncestor(file, place, false)) return true;
      }
    }

    boolean isAncestor = PsiTreeUtil.isAncestor(ancestor, place, false);
    if (!isAncestor && ancestor instanceof PsiNameIdentifierOwner owner) {
      PsiElement nameIdentifier = owner.getNameIdentifier();
      if (nameIdentifier != null && !PsiTreeUtil.isAncestor(ancestor, nameIdentifier, true)) {
        isAncestor = PsiTreeUtil.isAncestor(nameIdentifier.getParent(), place, false);
      }
    }

    if (!isAncestor) {
      InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(place.getProject());
      PsiLanguageInjectionHost host = injectedLanguageManager.getInjectionHost(place);
      while (host != null) {
        if (PsiTreeUtil.isAncestor(ancestor, host, false)) {
          isAncestor = true;
          break;
        }
        host = injectedLanguageManager.getInjectionHost(host);
      }
    }
    return isAncestor;
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    GlobalSearchScope searchScope = GlobalSearchScope.projectScope(myProject);
    for (PsiElement element : myElements) {
      boolean handled = false;
      for (SafeDeleteProcessorDelegate delegate: SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
        if (delegate.handlesElement(element)) {
          NonCodeUsageSearchInfo filter = delegate.findUsages(element, myElements, usages);
          if (filter != null) {
            for (PsiElement nonCodeUsageElement : filter.getElementsToSearch()) {
              addNonCodeUsages(nonCodeUsageElement, searchScope, usages,
                               filter.getInsideDeletedCondition(), mySearchNonJava, mySearchInCommentsAndStrings);
            }
          }
          handled = true;
          break;
        }
      }
      if (!handled && element instanceof PsiNamedElement) {
        findGenericElementUsages(element, usages, myElements);
        addNonCodeUsages(element, searchScope, usages, getDefaultInsideDeletedCondition(myElements), mySearchNonJava, 
                         mySearchInCommentsAndStrings);
      }
    }
    UsageInfo[] result = UsageViewUtil.removeDuplicatedUsages(usages.toArray(UsageInfo.EMPTY_ARRAY));
    Arrays.sort(result, (o1, o2) -> PsiUtilCore.compareElementsByPosition(o2.getElement(), o1.getElement()));
    return result;
  }

  public static Condition<PsiElement> getDefaultInsideDeletedCondition(PsiElement[] elements) {
    return usage -> !(usage instanceof PsiFile) && isInside(usage, elements);
  }

  public static void findGenericElementUsages(@NotNull PsiElement element, List<? super UsageInfo> usages, PsiElement[] allElementsToDelete,
                                              SearchScope scope) {
    ReferencesSearch.search(element, scope).forEach(reference -> {
      PsiElement refElement = reference.getElement();
      if (!isInside(refElement, allElementsToDelete)) {
        usages.add(new SafeDeleteReferenceSimpleDeleteUsageInfo(refElement, element, false));
      }
      return true;
    });
  }

  public static void findGenericElementUsages(PsiElement element, List<? super UsageInfo> usages, PsiElement[] allElementsToDelete) {
    findGenericElementUsages(element, usages, allElementsToDelete, element.getUseScope());
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usages = refUsages.get();
    MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();

    for (PsiElement element : myElements) {
      for (SafeDeleteProcessorDelegate delegate : SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
        if (delegate.handlesElement(element)) {
          delegate.findConflicts(element, myElements, usages, conflicts);

          // fallback for clients that are not updated for the new conflicts dialog:
          Collection<String> foundConflicts = delegate instanceof SafeDeleteProcessorDelegateBase base
                                              ? base.findConflicts(element, myElements, usages)
                                              : delegate.findConflicts(element, myElements);
          if (foundConflicts != null) conflicts.put(element, foundConflicts);
          break;
        }
      }
    }
    if (checkConflicts(usages, conflicts)) return false;

    UsageInfo[] preprocessedUsages = usages;
    for (SafeDeleteProcessorDelegate delegate: SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
      preprocessedUsages = delegate.preprocessUsages(myProject, preprocessedUsages);
      if (preprocessedUsages == null) return false;
    }

    HashSet<UsageInfo> diff = ContainerUtil.newHashSet(preprocessedUsages);
    Arrays.asList(usages).forEach(diff::remove);

    if (checkConflicts(diff.toArray(UsageInfo.EMPTY_ARRAY), new MultiMap<>())) return false;

    UsageInfo[] filteredUsages = UsageViewUtil.removeDuplicatedUsages(preprocessedUsages);
    prepareSuccessful(); // dialog is always dismissed
    refUsages.set(filteredUsages);
    return true;
  }

  private boolean checkConflicts(UsageInfo @NotNull [] usages, @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    boolean hasConflict = !conflicts.isEmpty();
    boolean hasUnsafeUsagesInCode = collectUnsafeUsages(usages, conflicts);

    if (hasConflict || hasUnsafeUsagesInCode) {
      RefactoringEventData conflictData = new RefactoringEventData();
      conflictData.putUserData(RefactoringEventData.CONFLICTS_KEY, conflicts.values());
      myProject.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .conflictsDetected("refactoring.safeDelete", conflictData);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        if (!ConflictsInTestsException.isTestIgnore()) throw new ConflictsInTestsException(conflicts.values());
      }
      else {
        ConflictsDialog dialog = new ConflictsDialog(myProject, conflicts);
        if (!dialog.showAndGet()) {
          prepareSuccessful(); // dialog is always dismissed;
          if (dialog.isShowConflicts()) {
            UsageInfo[] safeDeleteUsages = Arrays.stream(usages).
              filter(usage -> usage instanceof SafeDeleteReferenceUsageInfo info && !info.isSafeDelete()).toArray(UsageInfo[]::new);
            showUsages(safeDeleteUsages, usages);
          }
          return true;
        }
        else {
          myPreviewNonCodeUsages = false;
        }
      }
    }
    return false;
  }

  private void showUsages(UsageInfo @NotNull [] conflictUsages, UsageInfo @NotNull [] usages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText(LangBundle.message("tab.title.safe.delete.conflicts"));
    presentation.setTargetsNodeText(RefactoringBundle.message("attempting.to.delete.targets.node.text"));
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setCodeUsagesString(RefactoringBundle.message("safe.delete.conflict.title"));
    presentation.setNonCodeUsagesString(RefactoringBundle.message("occurrences.found.in.comments.strings.and.non.java.files"));
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    UsageViewManager manager = UsageViewManager.getInstance(myProject);
    UsageView usageView = showUsages(conflictUsages, presentation, manager);
    usageView.addPerformOperationAction(new RerunSafeDelete(myProject, myElements, usageView),
                                        RefactoringBundle.message("retry.command"), "", RefactoringBundle.message("rerun.safe.delete"));
    usageView.addPerformOperationAction(
      () -> {
        UsageInfo[] preprocessedUsages = usages;
        for (SafeDeleteProcessorDelegate delegate : SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
          preprocessedUsages = delegate.preprocessUsages(myProject, preprocessedUsages);
          if (preprocessedUsages == null) return;
        }
        execute(UsageViewUtil.removeDuplicatedUsages(preprocessedUsages));
      }, LangBundle.message("command.name.delete.anyway"), RefactoringBundle.message("usageView.need.reRun"),
      RefactoringBundle.message("usageView.doAction"));
  }

  private @NotNull UsageView showUsages(UsageInfo @NotNull [] usages, @NotNull UsageViewPresentation presentation, 
                                        @NotNull UsageViewManager manager) {
    for (SafeDeleteProcessorDelegate delegate : SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
       if (delegate instanceof SafeDeleteProcessorDelegateBase base) {
         UsageView view = base.showUsages(usages, presentation, manager, myElements);
         if (view != null) return view;
       }
    }
    UsageTarget[] targets = new UsageTarget[myElements.length];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = new PsiElement2UsageTargetAdapter(myElements[i]);
    }

    return manager.showUsages(targets, UsageInfoToUsageConverter.convert(myElements, usages), presentation);
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  private static final class RerunSafeDelete implements Runnable {
    final SmartPsiElementPointer<?>[] myPointers;
    private final Project myProject;
    private final UsageView myUsageView;

    RerunSafeDelete(Project project, PsiElement[] elements, UsageView usageView) {
      myProject = project;
      myUsageView = usageView;
      myPointers = new SmartPsiElementPointer[elements.length];
      for (int i = 0; i < elements.length; i++) {
        PsiElement element = elements[i];
        myPointers[i] = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
      }
    }

    @Override
    public void run() {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myUsageView.close();
      ArrayList<PsiElement> elements = new ArrayList<>();
      for (SmartPsiElementPointer<?> pointer : myPointers) {
        PsiElement element = pointer.getElement();
        if (element != null) {
          elements.add(element);
        }
      }
      if (!elements.isEmpty()) {
        SafeDeleteHandler.invoke(myProject, PsiUtilCore.toPsiElementArray(elements), true);
      }
    }
  }

  /**
   * @return true when any unsafe usages are in code, false when there are no unsafe changes,
   * or they are only in string, comments and generated files
   */
  private static boolean collectUnsafeUsages(UsageInfo @NotNull [] usages, @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    boolean codeUsages = false;
    for (UsageInfo usage : usages) {
      if (usage instanceof SafeDeleteReferenceUsageInfo info) {
        if (info.isSafeDelete()) continue;
        String description = RefactoringUIUtil.getDescription(info.getReferencedElement(), true);
        if (usage.isNonCodeUsage) {
          conflicts.putValue(info.getElement(), RefactoringBundle.message("non.code.usage.that.is.not.safe.to.delete", description));
        }
        else if (isInGeneratedCode(info, info.getProject())) {
          conflicts.putValue(info.getElement(), RefactoringBundle.message("generated.code.usage.that.is.not.safe.to.delete", description));
        }
        else {
          codeUsages = true;
          conflicts.putValue(info.getElement(), RefactoringBundle.message("usage.that.is.not.safe.to.delete", description));
        }
      }
    }
    return codeUsages;
  }

  private static boolean isInGeneratedCode(SafeDeleteReferenceUsageInfo usage, Project project) {
    VirtualFile file = usage.getVirtualFile();
    return file != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == myElements.length);
    System.arraycopy(elements, 0, myElements, 0, elements.length);
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    if (myPreviewNonCodeUsages && UsageViewUtil.reportNonRegularUsages(usages, myProject)) return true;

    return super.isPreviewUsages(filterToBeDeleted(usages));
  }

  private static UsageInfo[] filterToBeDeleted(UsageInfo[] usages) {
    ArrayList<UsageInfo> list = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (!(usage instanceof SafeDeleteReferenceUsageInfo info) || info.isSafeDelete()) {
        list.add(usage);
      }
    }
    return list.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected @NotNull RefactoringEventData getBeforeData() {
    RefactoringEventData beforeData = new RefactoringEventData();
    beforeData.addElements(myElements);
    return beforeData;
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return "refactoring.safeDelete";
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    try {
      SmartPointerManager pointerManager = SmartPointerManager.getInstance(myProject);
      List<SmartPsiElementPointer<PsiElement>> pointers = ContainerUtil.map(myElements, pointerManager::createSmartPsiElementPointer);

      for (UsageInfo usage : usages) {
        if (usage instanceof SafeDeleteCustomUsageInfo info) {
          info.performRefactoring();
        }
      }

      for (PsiElement element : myElements) {
        for (SafeDeleteProcessorDelegate delegate : SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
          if (delegate.handlesElement(element)) {
            delegate.prepareForDeletion(element);
            break;
          }
        }
      }

      for (SmartPsiElementPointer<PsiElement> pointer : pointers) {
        PsiElement element = pointer.getElement();
        if (element != null) {
          element.delete();
        }
      }
      if (myAfterRefactoringCallback != null) myAfterRefactoringCallback.run();
    } catch (IncorrectOperationException e) {
      RefactoringUIUtil.processIncorrectOperation(myProject, e);
    }
  }

  private @Command String myCachedCommandName;
  @Override
  protected @NotNull String getCommandName() {
    if (myCachedCommandName == null) {
      myCachedCommandName =
        RefactoringBundle.message("safe.delete.command", RefactoringUIUtil.calculatePsiElementDescriptionList(myElements));
    }
    return myCachedCommandName;
  }

  public static void addNonCodeUsages(PsiElement element,
                                      SearchScope searchScope,
                                      List<? super UsageInfo> usages,
                                      @Nullable Condition<? super PsiElement> insideElements,
                                      boolean searchNonJava,
                                      boolean searchInCommentsAndStrings) {
    UsageInfoFactory nonCodeUsageFactory = (usage, startOffset, endOffset) -> {
      if (insideElements != null && insideElements.value(usage)) return null;
      return new SafeDeleteReferenceSimpleDeleteUsageInfo(usage, element, startOffset, endOffset, true, false);
    };
    if (searchInCommentsAndStrings) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
      TextOccurrencesUtil.addUsagesInStringsAndComments(element, searchScope, stringToSearch, usages, nonCodeUsageFactory);
    }
    if (searchNonJava && searchScope instanceof GlobalSearchScope scope) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(element, NonCodeSearchDescriptionLocation.NON_JAVA);
      TextOccurrencesUtil.addTextOccurrences(element, stringToSearch, scope, usages, nonCodeUsageFactory);
    }
  }

  @Override
  protected boolean isToBeChanged(@NotNull UsageInfo usageInfo) {
    if (usageInfo instanceof SafeDeleteReferenceUsageInfo info) {
      return info.isSafeDelete() && super.isToBeChanged(usageInfo);
    }
    return super.isToBeChanged(usageInfo);
  }

  public static boolean validElement(@NotNull PsiElement element) {
    if (element instanceof PsiFile) return true;
    RefactoringSupportProvider provider = LanguageRefactoringSupport.getInstance().forContext(element);
    return provider != null && provider.isSafeDeleteAvailable(element);
  }

  @Contract("_, _, _, _, _ -> new")
  public static @NotNull SafeDeleteProcessor createInstance(@NotNull Project project, @Nullable Runnable prepareSuccessfulCallback,
                                                            PsiElement @NotNull [] elementsToDelete, boolean isSearchInComments, 
                                                            boolean isSearchNonJava) {
    return new SafeDeleteProcessor(project, prepareSuccessfulCallback, elementsToDelete, isSearchInComments, isSearchNonJava);
  }

  @Contract("_, _, _, _, _, _ -> new")
  public static @NotNull SafeDeleteProcessor createInstance(@NotNull Project project, @Nullable Runnable prepareSuccessfulCallBack,
                                                            PsiElement @NotNull [] elementsToDelete, boolean isSearchInComments,
                                                            boolean isSearchNonJava, boolean askForAccessors) {
    ArrayList<PsiElement> elements = new ArrayList<>(Arrays.asList(elementsToDelete));
    Set<PsiElement> elementsToDeleteSet = ContainerUtil.newHashSet(elementsToDelete);

    for (PsiElement psiElement : elementsToDelete) {
      for (SafeDeleteProcessorDelegate delegate : SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
        if (delegate.handlesElement(psiElement)) {
          Collection<PsiElement> addedElements = delegate.getAdditionalElementsToDelete(psiElement, elementsToDeleteSet, askForAccessors);
          if (addedElements != null) {
            elements.addAll(addedElements);
          }
          break;
        }
      }
    }

    return new SafeDeleteProcessor(project, prepareSuccessfulCallBack,
                                   PsiUtilCore.toPsiElementArray(elements),
                                   isSearchInComments, isSearchNonJava);
  }

  public boolean isSearchInCommentsAndStrings() {
    return mySearchInCommentsAndStrings;
  }

  public void setSearchInCommentsAndStrings(boolean searchInCommentsAndStrings) {
    mySearchInCommentsAndStrings = searchInCommentsAndStrings;
  }

  public boolean isSearchNonJava() {
    return mySearchNonJava;
  }

  public void setSearchNonJava(boolean searchNonJava) {
    mySearchNonJava = searchNonJava;
  }

  @Override
  protected boolean skipNonCodeUsages() {
    return true;
  }

  public void setAfterRefactoringCallback(Runnable afterRefactoringCallback) {
    myAfterRefactoringCallback = afterRefactoringCallback;
  }
}
