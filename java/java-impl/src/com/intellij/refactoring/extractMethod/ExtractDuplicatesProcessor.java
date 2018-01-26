// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.MatchProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class ExtractDuplicatesProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ExtractDuplicatesProcessor.class);

  private final PsiMethod myMethod;
  private final List<Couple<SmartPsiElementPointer<PsiElement>>> myDuplicatesPointers = new ArrayList<>();
  private final ExtractMethodSnapshot myExtractMethodSnapshot;
  private final List<SmartPsiElementPointer<PsiElement>> myElementPointers;

  public ExtractDuplicatesProcessor(ExtractMethodProcessor processor) {
    super(processor.getProject());

    myExtractMethodSnapshot = new ExtractMethodSnapshot(processor);

    PsiElement[] elements = processor.getElements();
    PsiMethod method = processor.generateEmptyMethod(processor.getMethodName(), processor.getTargetClass().getLBrace());

    List<Match> duplicates = processor.getDuplicates();
    if (ContainerUtil.isEmpty(duplicates)) {
      ParametrizedDuplicates parametrizedDuplicates = processor.getParametrizedDuplicates();
      if (parametrizedDuplicates != null) {
        duplicates = parametrizedDuplicates.getDuplicates();
        method = parametrizedDuplicates.getParametrizedMethod();
      }
      else {
        duplicates = Collections.emptyList();
      }
    }
    myMethod = method;

    SmartPointerManager manager = SmartPointerManager.getInstance(processor.getProject());
    myElementPointers = StreamEx.of(elements).map(manager::createSmartPsiElementPointer).toList();

    myDuplicatesPointers.add(createPointerPair(elements[0], elements[elements.length - 1], manager));
    for (Match duplicate : duplicates) {
      myDuplicatesPointers.add(createPointerPair(duplicate.getMatchStart(), duplicate.getMatchEnd(), manager));
    }

    setPreviewUsages(true);
  }

  Couple<SmartPsiElementPointer<PsiElement>> createPointerPair(@NotNull PsiElement start,
                                                               @NotNull PsiElement end,
                                                               @NotNull SmartPointerManager manager) {
    if (start == end) {
      SmartPsiElementPointer<PsiElement> pointer = manager.createSmartPsiElementPointer(start);
      return Couple.of(pointer, pointer);
    }
    SmartPsiElementPointer<PsiElement> startPointer = manager.createSmartPsiElementPointer(start);
    SmartPsiElementPointer<PsiElement> endPointer = manager.createSmartPsiElementPointer(end);
    return Couple.of(startPointer, endPointer);
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myMethod};
      }

      @Override
      public String getProcessedElementsHeader() {
        return RefactoringBundle.message("refactoring.extract.method.preview.tree.method");
      }

      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactoringBundle.message("refactoring.extract.method.preview.tree.duplicates", usagesCount, filesCount);
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    List<UsageInfo> infos = new ArrayList<>();
    for (Couple<SmartPsiElementPointer<PsiElement>> pointers : myDuplicatesPointers) {
      PsiElement start = pointers.getFirst().getElement();
      PsiElement end = pointers.getSecond().getElement();
      if (start != null && end != null && start.isValid() && end.isValid()) {
        infos.add(MyUsageInfo.create(start, end));
      }
    }
    return infos.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    if (usages.length == 0) return;
    PsiElement[] elements = StreamEx.of(myElementPointers)
                                    .map(SmartPsiElementPointer::getElement)
                                    .toArray(PsiElement.EMPTY_ARRAY);
    LOG.assertTrue(!ArrayUtil.contains(null, elements));

    JavaDuplicatesExtractMethodProcessor myProcessor =
      new JavaDuplicatesExtractMethodProcessor(elements, ExtractMethodHandler.REFACTORING_NAME);
    myProcessor.applyFromSnapshot(myExtractMethodSnapshot);
    myProcessor.setShowErrorDialogs(true);
    try {
      if (!myProcessor.prepare(null)) return;
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil.showErrorHint(myProject, null, e.getMessage(), ExtractMethodHandler.REFACTORING_NAME, HelpID.EXTRACT_METHOD);
    }

    ExtractMethodHandler.extractMethod(myProject, myProcessor);
    myProcessor.initParametrizedDuplicates(false);
    PsiMethodCallExpression call = myProcessor.getMethodCall();
    Editor editor = EditorHelper.openInEditor(call);
    if (editor != null) {
      MatchProvider provider = new MyMatchProvider(myProcessor, usages);
      DuplicatesImpl.invoke(myProject, provider, false);
    }
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("refactoring.extract.method.preview.command");
  }

  @Nullable
  public static PsiElement[] getUsageInfoElements(@NotNull UsageInfo usageInfo) {
    Segment segment = usageInfo.getSegment();
    PsiFile file = usageInfo.getFile();
    if (segment != null && file != null) {
      PsiElement[] elements = getSegmentElements(segment, file);
      if (elements.length != 0) {
        return elements;
      }
    }
    return null;
  }

  @NotNull
  public static PsiElement[] getSegmentElements(@NotNull Segment textRange, @NotNull PsiFile file) {
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, textRange.getStartOffset(), textRange.getEndOffset());
    if (expr != null) {
      return new PsiElement[]{expr};
    }
    return CodeInsightUtil.findStatementsInRange(file, textRange.getStartOffset(), textRange.getEndOffset());
  }


  private static class MyUsageInfo extends UsageInfo {
    private MyUsageInfo(int startOffset, int endOffset, @NotNull PsiElement parent) {
      super(parent, startOffset, endOffset);
    }

    private MyUsageInfo(@NotNull PsiElement element) {
      super(element);
    }

    static MyUsageInfo create(@NotNull PsiElement start, @NotNull PsiElement end) {
      if (start == end) {
        return new MyUsageInfo(start);
      }
      PsiElement parent = start.getParent();
      int parentOffset = parent.getTextRange().getStartOffset();
      int startOffset = start.getTextRange().getStartOffset() - parentOffset;
      int endOffset = end.getTextRange().getEndOffset() - parentOffset;
      return new MyUsageInfo(startOffset, endOffset, parent);
    }
  }

  private static class MyMatchProvider implements MatchProvider {
    private final JavaDuplicatesExtractMethodProcessor myProcessor;
    private final UsageInfo[] myUsages;

    public MyMatchProvider(JavaDuplicatesExtractMethodProcessor processor, UsageInfo[] usages) {
      myProcessor = processor;
      myUsages = usages;
    }

    @Override
    public void prepareSignature(Match match) {
      myProcessor.prepareSignature(match);
    }

    @Override
    public PsiElement processMatch(Match match) throws IncorrectOperationException {
      return myProcessor.processMatch(match);
    }

    @Override
    public List<Match> getDuplicates() {
      Set<PsiElement> selected = new THashSet<>();
      for (UsageInfo usage : myUsages) {
        PsiElement[] elements = getUsageInfoElements(usage);
        if (elements != null && elements.length != 0 && elements[0] != null && elements[0].isValid()) {
          selected.add(elements[0]);
        }
      }

      List<Match> duplicates = myProcessor.getDuplicates();
      return ContainerUtil.filter(duplicates, d -> selected.contains(d.getMatchStart()));
    }

    @Nullable
    @Override
    public Boolean hasDuplicates() {
      return myUsages.length > 1 ? null : false;
    }

    @Nullable
    @Override
    public String getConfirmDuplicatePrompt(Match match) {
      return null;
    }

    @Override
    public String getReplaceDuplicatesTitle(int idx, int size) {
      return myProcessor.getReplaceDuplicatesTitle(idx, size);
    }
  }
}
