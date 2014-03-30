/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.compiler.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.compiler.options.ValidationConfiguration;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class InspectionValidatorWrapper implements Validator {
  private final InspectionValidator myValidator;
  private final PsiManager myPsiManager;
  private final CompilerManager myCompilerManager;
  private final InspectionManager myInspectionManager;
  private final InspectionProjectProfileManager myProfileManager;
  private final PsiDocumentManager myPsiDocumentManager;
  private static final ThreadLocal<Boolean> ourCompilationThreads = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  public InspectionValidatorWrapper(final CompilerManager compilerManager, final InspectionManager inspectionManager,
                                    final InspectionProjectProfileManager profileManager, final PsiDocumentManager psiDocumentManager,
                                    final PsiManager psiManager, final InspectionValidator validator) {
    myCompilerManager = compilerManager;
    myInspectionManager = inspectionManager;
    myProfileManager = profileManager;
    myPsiDocumentManager = psiDocumentManager;
    myPsiManager = psiManager;
    myValidator = validator;
  }

  public static boolean isCompilationThread() {
    return ourCompilationThreads.get().booleanValue();
  }

  private static List<ProblemDescriptor> runInspectionOnFile(@NotNull PsiFile file,
                                                            @NotNull LocalInspectionTool inspectionTool) {
    InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(file.getProject());
    GlobalInspectionContext context = inspectionManager.createNewGlobalContext(false);
    return InspectionEngine.runInspectionOnFile(file, new LocalInspectionToolWrapper(inspectionTool), context);
  }

  private class MyValidatorProcessingItem implements ProcessingItem {
    private final VirtualFile myVirtualFile;
    private final PsiFile myPsiFile;
    private PsiElementsValidityState myValidityState;

    public MyValidatorProcessingItem(@NotNull final PsiFile psiFile) {
      myPsiFile = psiFile;
      myVirtualFile = psiFile.getVirtualFile();
    }

    @Override
    @NotNull
    public VirtualFile getFile() {
      return myVirtualFile;
    }

    @Override
    @Nullable
    public ValidityState getValidityState() {
      if (myValidityState == null) {
        myValidityState = computeValidityState();
      }
      return myValidityState;
    }

    private PsiElementsValidityState computeValidityState() {
      final PsiElementsValidityState state = new PsiElementsValidityState();
      for (PsiElement psiElement : myValidator.getDependencies(myPsiFile)) {
        state.addDependency(psiElement);
      }
      return state;
    }

    public PsiFile getPsiFile() {
      return myPsiFile;
    }
  }

  @Override
  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    final Project project = context.getProject();
    if (project.isDefault() || !ValidationConfiguration.shouldValidate(this, context)) {
      return ProcessingItem.EMPTY_ARRAY;
    }
    final ExcludedEntriesConfiguration excludedEntriesConfiguration = ValidationConfiguration.getExcludedEntriesConfiguration(project);
    final List<ProcessingItem> items = new ReadAction<List<ProcessingItem>>() {
      @Override
      protected void run(final Result<List<ProcessingItem>> result) {
        final CompileScope compileScope = context.getCompileScope();
        if (!myValidator.isAvailableOnScope(compileScope)) return;

        final ArrayList<ProcessingItem> items = new ArrayList<ProcessingItem>();

        final Processor<VirtualFile> processor = new ReadActionProcessor<VirtualFile>() {
          @Override
          public boolean processInReadAction(VirtualFile file) {
            if (!file.isValid()) {
              return true;
            }

            if (myCompilerManager.isExcludedFromCompilation(file) ||
                excludedEntriesConfiguration.isExcluded(file)) {
              return true;
            }

            final Module module = context.getModuleByFile(file);
            if (module != null) {
              final PsiFile psiFile = myPsiManager.findFile(file);
              if (psiFile != null) {
                items.add(new MyValidatorProcessingItem(psiFile));
              }
            }
            return true;
          }
        };
        ContainerUtil.process(myValidator.getFilesToProcess(myPsiManager.getProject(), context), processor);

        result.setResult(items);
      }
    }.execute().getResultObject();
    if (items == null) return ProcessingItem.EMPTY_ARRAY;

    return items.toArray(new ProcessingItem[items.size()]);
  }

  @Override
  public ProcessingItem[] process(final CompileContext context, final ProcessingItem[] items) {
    context.getProgressIndicator().setText(myValidator.getProgressIndicatorText());

    final List<ProcessingItem> processedItems = new ArrayList<ProcessingItem>();
    final List<LocalInspectionTool> inspections = new ArrayList<LocalInspectionTool>();
    for (final Class aClass : myValidator.getInspectionToolClasses(context)) {
      try {
        inspections.add((LocalInspectionTool)aClass.newInstance());
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new Error(e);
      }
    }
    for (int i = 0; i < items.length; i++) {
      final MyValidatorProcessingItem item = (MyValidatorProcessingItem)items[i];
      context.getProgressIndicator().checkCanceled();
      context.getProgressIndicator().setFraction((double)i / items.length);

      try {
        ourCompilationThreads.set(Boolean.TRUE);

        if (checkFile(inspections, item.getPsiFile(), context)) {
          processedItems.add(item);
        }
      }
      finally {
        ourCompilationThreads.set(Boolean.FALSE);
      }
    }

    return processedItems.toArray(new ProcessingItem[processedItems.size()]);
  }

  private boolean checkFile(List<LocalInspectionTool> inspections, final PsiFile file, final CompileContext context) {
    if (!checkUnderReadAction(file, context, new Computable<Map<ProblemDescriptor, HighlightDisplayLevel>>() {
      @Override
      public Map<ProblemDescriptor, HighlightDisplayLevel> compute() {
        return myValidator.checkAdditionally(file);
      }
    })) {
      return false;
    }

    if (!checkUnderReadAction(file, context, new Computable<Map<ProblemDescriptor, HighlightDisplayLevel>>() {
      @Override
      public Map<ProblemDescriptor, HighlightDisplayLevel> compute() {
        if (file instanceof XmlFile) {
          return runXmlFileSchemaValidation((XmlFile)file);
        }
        return Collections.emptyMap();
      }
    })) return false;


    final InspectionProfile inspectionProfile = myProfileManager.getInspectionProfile();
    for (final LocalInspectionTool inspectionTool : inspections) {
      if (!checkUnderReadAction(file, context, new Computable<Map<ProblemDescriptor, HighlightDisplayLevel>>() {
        @Override
        public Map<ProblemDescriptor, HighlightDisplayLevel> compute() {
          if (getHighlightDisplayLevel(inspectionTool, inspectionProfile, file) != HighlightDisplayLevel.DO_NOT_SHOW) {
            return runInspectionTool(file, inspectionTool, getHighlightDisplayLevel(inspectionTool, inspectionProfile, file)
            );
          }
          return Collections.emptyMap();
        }
      })) return false;
    }
    return true;
  }

  private boolean checkUnderReadAction(PsiFile file, CompileContext context, Computable<Map<ProblemDescriptor, HighlightDisplayLevel>> runnable) {
    AccessToken token = ReadAction.start();
    try {
      if (!file.isValid()) return false;

      final Document document = myPsiDocumentManager.getCachedDocument(file);
      if (document != null && myPsiDocumentManager.isUncommited(document)) {
        final String url = file.getViewProvider().getVirtualFile().getUrl();
        context.addMessage(CompilerMessageCategory.WARNING, CompilerBundle.message("warning.text.file.has.been.changed"), url, -1, -1);
        return false;
      }

      if (reportProblems(context, runnable.compute())) return false;
    }
    finally {
      token.finish();
    }
    return true;
  }

  private boolean reportProblems(CompileContext context, Map<ProblemDescriptor, HighlightDisplayLevel> problemsMap) {
    if (problemsMap.isEmpty()) {
      return false;
    }

    for (Map.Entry<ProblemDescriptor, HighlightDisplayLevel> entry : problemsMap.entrySet()) {
      ProblemDescriptor problemDescriptor = entry.getKey();
      final PsiElement element = problemDescriptor.getPsiElement();
      final PsiFile psiFile = element.getContainingFile();
      if (psiFile == null) continue;

      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;

      final CompilerMessageCategory category = myValidator.getCategoryByHighlightDisplayLevel(entry.getValue(), virtualFile, context);
      final Document document = myPsiDocumentManager.getDocument(psiFile);

      final int offset = problemDescriptor.getStartElement().getTextOffset();
      assert document != null;
      final int line = document.getLineNumber(offset);
      final int column = offset - document.getLineStartOffset(line);
      context.addMessage(category, problemDescriptor.getDescriptionTemplate(), virtualFile.getUrl(), line + 1, column + 1);
    }
    return true;
  }

  private static Map<ProblemDescriptor, HighlightDisplayLevel> runInspectionTool(final PsiFile file,
                                                                                 final LocalInspectionTool inspectionTool,
                                                                                 final HighlightDisplayLevel level) {
    Map<ProblemDescriptor, HighlightDisplayLevel> problemsMap = new LinkedHashMap<ProblemDescriptor, HighlightDisplayLevel>();
    for (ProblemDescriptor descriptor : runInspectionOnFile(file, inspectionTool)) {
      problemsMap.put(descriptor, level);
    }
    return problemsMap;
  }

  private static HighlightDisplayLevel getHighlightDisplayLevel(final LocalInspectionTool inspectionTool,
                                                                final InspectionProfile inspectionProfile, PsiElement file) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool.getShortName());
    return inspectionProfile.isToolEnabled(key, file) ? inspectionProfile.getErrorLevel(key, file) : HighlightDisplayLevel.DO_NOT_SHOW;
  }

  private Map<ProblemDescriptor, HighlightDisplayLevel> runXmlFileSchemaValidation(@NotNull XmlFile xmlFile) {
    final AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSession(xmlFile));

    final List<ExternalAnnotator> annotators = ExternalLanguageAnnotators.allForFile(StdLanguages.XML, xmlFile);
    for (ExternalAnnotator annotator : annotators) {
      Object initial = annotator.collectInformation(xmlFile);
      if (initial != null) {
        Object result = annotator.doAnnotate(initial);
        if (result != null) {
          annotator.apply(xmlFile, result, holder);
        }
      }
    }

    if (!holder.hasAnnotations()) return Collections.emptyMap();

    Map<ProblemDescriptor, HighlightDisplayLevel> problemsMap = new LinkedHashMap<ProblemDescriptor, HighlightDisplayLevel>();
    for (final Annotation annotation : holder) {
      final HighlightInfo info = HighlightInfo.fromAnnotation(annotation);
      if (info.getSeverity() == HighlightSeverity.INFORMATION) continue;

      final PsiElement startElement = xmlFile.findElementAt(info.startOffset);
      final PsiElement endElement = info.startOffset == info.endOffset ? startElement : xmlFile.findElementAt(info.endOffset - 1);
      if (startElement == null || endElement == null) continue;

      final ProblemDescriptor descriptor =
        myInspectionManager.createProblemDescriptor(startElement, endElement, info.getDescription(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                    false);
      final HighlightDisplayLevel level = info.getSeverity() == HighlightSeverity.ERROR? HighlightDisplayLevel.ERROR: HighlightDisplayLevel.WARNING;
      problemsMap.put(descriptor, level);
    }
    return problemsMap;
  }


  @Override
  @NotNull
  public String getDescription() {
    return myValidator.getDescription();
  }

  @Override
  public boolean validateConfiguration(final CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(final DataInput in) throws IOException {
    return PsiElementsValidityState.load(in);
  }

}
