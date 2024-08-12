// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.UndoRefactoringElementListener;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.NlsContexts.Command;
import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * @author Maxim.Medvedev
 */
public abstract class ChangeSignatureProcessorBase extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ChangeSignatureProcessorBase.class);
  protected static final String REFACTORING_ID = "refactoring.changeSignature";

  protected final ChangeInfo myChangeInfo;
  protected final PsiManager myManager;

  protected ChangeSignatureProcessorBase(Project project, ChangeInfo changeInfo) {
    super(project);
    myChangeInfo = changeInfo;
    myManager = PsiManager.getInstance(project);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return findUsages(myChangeInfo);
  }

  public static void collectConflictsFromExtensions(final @NotNull Ref<UsageInfo[]> refUsages,
                                                    final MultiMap<PsiElement, @DialogMessage String> conflictDescriptions,
                                                    final ChangeInfo changeInfo) {
    Computable<Boolean> computable = () -> {
      for (ChangeSignatureUsageProcessor usageProcessor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
        final MultiMap<PsiElement, @DialogMessage String> conflicts = usageProcessor.findConflicts(changeInfo, refUsages);
        for (PsiElement key : conflicts.keySet()) {
          Collection<String> collection = conflictDescriptions.get(key);
          if (collection.isEmpty()) collection = new HashSet<>();
          collection.addAll(conflicts.get(key));
          conflictDescriptions.put(key, collection);
        }
      }
      return true;
    };
    ActionUtil.underModalProgress(changeInfo.getMethod().getProject(), RefactoringBundle.message("detecting.possible.conflicts"), computable);
  }

  public static UsageInfo @NotNull [] findUsages(ChangeInfo changeInfo) {
    List<UsageInfo> infos = new ArrayList<>();
    final ChangeSignatureUsageProcessor[] processors = ChangeSignatureUsageProcessor.EP_NAME.getExtensions();
    for (ChangeSignatureUsageProcessor processor : processors) {
      for (UsageInfo info : processor.findUsages(changeInfo)) {
        if (info == null) {
          PluginException.logPluginError(LOG, "findUsages() returns null items in " + processor.getClass().getName(), null, processor.getClass());
          continue;
        }
        infos.add(info);
      }
    }
    infos = filterUsages(infos);
    return infos.toArray(UsageInfo.EMPTY_ARRAY);
  }

  protected static List<UsageInfo> filterUsages(List<? extends UsageInfo> infos) {
    Map<PsiElement, MoveRenameUsageInfo> moveRenameInfos = new LinkedHashMap<>();
    Set<PsiElement> usedElements = new HashSet<>();

    List<UsageInfo> result = new ArrayList<>(infos.size() / 2);
    for (UsageInfo info : infos) {
      LOG.assertTrue(info != null);
      PsiElement element = info.getElement();
      if (info instanceof MoveRenameUsageInfo) {
        if (usedElements.contains(element)) continue;
        moveRenameInfos.put(element, (MoveRenameUsageInfo)info);
      }
      else {
        moveRenameInfos.remove(element);
        usedElements.add(element);
        if (!(info instanceof PossiblyIncorrectUsage) || ((PossiblyIncorrectUsage)info).isCorrect()) {
          result.add(info);
        }
      }
    }
    result.addAll(moveRenameInfos.values());
    return result;
  }


  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
      if (processor.shouldPreviewUsages(myChangeInfo, usages)) return true;
    }
    return super.isPreviewUsages(usages);
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return REFACTORING_ID;
  }

  @Override
  protected @Nullable RefactoringEventData getBeforeData() {
    RefactoringEventData data = new RefactoringEventData();
    ChangeInfo changeInfo = getChangeInfo();
    data.addElement(changeInfo.getMethod());
    List<String> defaultValues = new ArrayList<>();
    for (ParameterInfo parameter : changeInfo.getNewParameters()) {
      if (parameter.getOldIndex() == -1) {
        ContainerUtil.addIfNotNull(defaultValues, parameter.getDefaultValue());
      }
    }

    if (!defaultValues.isEmpty()) {
      data.addStringProperties(ArrayUtil.toStringArray(defaultValues));
    }

    return data;
  }

  @Override
  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElement(getChangeInfo().getMethod());
    return data;
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    RefactoringTransaction transaction = getTransaction();
    final ChangeInfo changeInfo = myChangeInfo;
    PsiElement method = changeInfo.getMethod();
    final RefactoringElementListener elementListener = transaction == null ? null : transaction.getElementListener(method);
    final String fqn = CopyReferenceAction.elementToFqn(method);
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(method);
    if (fqn != null) {
      UndoManager.getInstance(myProject).undoableActionPerformed(new UndoChangeSignatureAction(elementListener, pointer, fqn));
    }
    try {
      doChangeSignature(changeInfo, usages);
      method = pointer.getElement();
      LOG.assertTrue(method != null && method.isValid());
      if (elementListener != null && changeInfo.isNameChanged()) {
        elementListener.elementRenamed(method);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static void doChangeSignature(ChangeInfo changeInfo, UsageInfo @NotNull [] usages) {
    final ChangeSignatureUsageProcessor[] processors = ChangeSignatureUsageProcessor.EP_NAME.getExtensions();

    final ResolveSnapshotProvider resolveSnapshotProvider = changeInfo.isParameterNamesChanged() ?
                                                            VariableInplaceRenamer.INSTANCE.forLanguage(changeInfo.getMethod().getLanguage()) : null;
    final List<ResolveSnapshotProvider.ResolveSnapshot> snapshots = new ArrayList<>();
    for (ChangeSignatureUsageProcessor processor : processors) {
      if (resolveSnapshotProvider != null) {
        processor.registerConflictResolvers(snapshots, resolveSnapshotProvider, usages, changeInfo);
      }
    }

    for (UsageInfo usage : usages) {
      for (ChangeSignatureUsageProcessor processor : processors) {
        if (processor.processUsage(changeInfo, usage, true, usages)) break;
      }
    }

    LOG.assertTrue(changeInfo.getMethod().isValid());
    for (ChangeSignatureUsageProcessor processor : processors) {
      if (processor.processPrimaryMethod(changeInfo)) break;
    }

    for (UsageInfo usage : usages) {
      for (ChangeSignatureUsageProcessor processor : processors) {
        if (processor.processUsage(changeInfo, usage, false, usages)) break;
      }
    }

    if (!snapshots.isEmpty()) {
      for (ParameterInfo parameterInfo : changeInfo.getNewParameters()) {
        for (ResolveSnapshotProvider.ResolveSnapshot snapshot : snapshots) {
          snapshot.apply(parameterInfo.getName());
        }
      }
    }
  }

  @Override
  protected @NotNull @Command String getCommandName() {
    return RefactoringBundle.message("changing.signature.of.0", DescriptiveNameUtil.getDescriptiveName(myChangeInfo.getMethod()));
  }

  public ChangeInfo getChangeInfo() {
    return myChangeInfo;
  }

  private static class UndoChangeSignatureAction extends BasicUndoableAction {
    private final RefactoringElementListener myElementListener;
    private final SmartPsiElementPointer<PsiElement> myPointer;
    private final String myFqn;

    private UndoChangeSignatureAction(RefactoringElementListener elementListener, SmartPsiElementPointer<PsiElement> pointer, String fqn) {
      myElementListener = elementListener;
      myPointer = pointer;
      myFqn = fqn;
    }

    @Override
    public void undo() {
      if (myElementListener instanceof UndoRefactoringElementListener listener) {
        PsiElement element = myPointer.getElement();
        if (element != null) {
          listener.undoElementMovedOrRenamed(element, myFqn);
        }
      }
    }

    @Override
    public void redo() { }
  }
}
