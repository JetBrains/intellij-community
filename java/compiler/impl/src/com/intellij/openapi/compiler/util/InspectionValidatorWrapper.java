/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.compiler.options.ValidationConfiguration;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.util.*;

public class InspectionValidatorWrapper implements Validator {
  private final InspectionValidator myValidator;
  private final PsiManager myPsiManager;
  private final CompilerManager myCompilerManager;
  private final InspectionManager myInspectionManager;
  private final InspectionProjectProfileManager myProfileManager;
  private final PsiDocumentManager myPsiDocumentManager;

  private static final ThreadLocal<Boolean> ourCompilationThreads = ThreadLocal.withInitial(() -> Boolean.FALSE);

  private InspectionValidatorWrapper(@NotNull CompilerManager compilerManager,
                                     @NotNull InspectionManager inspectionManager,
                                     @NotNull InspectionProjectProfileManager profileManager,
                                     @NotNull PsiDocumentManager psiDocumentManager,
                                     @NotNull PsiManager psiManager,
                                     @NotNull InspectionValidator validator) {
    myCompilerManager = compilerManager;
    myInspectionManager = inspectionManager;
    myProfileManager = profileManager;
    myPsiDocumentManager = psiDocumentManager;
    myPsiManager = psiManager;
    myValidator = validator;
  }

  @NotNull
  public static InspectionValidatorWrapper create(@NotNull Project project, @NotNull InspectionValidator validator) {
    return new InspectionValidatorWrapper(
      CompilerManager.getInstance(project),
      InspectionManager.getInstance(project),
      InspectionProjectProfileManager.getInstance(project),
      PsiDocumentManager.getInstance(project),
      PsiManager.getInstance(project),
      validator
    );
  }

  public static boolean isCompilationThread() {
    return ourCompilationThreads.get().booleanValue();
  }

  private static List<ProblemDescriptor> runInspectionOnFile(@NotNull PsiFile file, @NotNull LocalInspectionTool inspectionTool) {
    InspectionManager inspectionManager = InspectionManager.getInstance(file.getProject());
    GlobalInspectionContext context = inspectionManager.createNewGlobalContext();
    return InspectionEngine.runInspectionOnFile(file, new LocalInspectionToolWrapper(inspectionTool), context);
  }

  private class MyValidatorProcessingItem implements ProcessingItem {
    private final VirtualFile myVirtualFile;
    private final PsiManager myPsiManager;
    private PsiElementsValidityState myValidityState;

    MyValidatorProcessingItem(@NotNull PsiFile psiFile) {
      myVirtualFile = psiFile.getVirtualFile();
      myPsiManager = psiFile.getManager();
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
      PsiElementsValidityState state = new PsiElementsValidityState();
      PsiFile psiFile = getPsiFile();
      if (psiFile != null) {
        for (PsiElement psiElement : myValidator.getDependencies(psiFile)) {
          state.addDependency(psiElement);
        }
      }
      return state;
    }

    @Nullable 
    public PsiFile getPsiFile() {
      return myVirtualFile.isValid() ? myPsiManager.findFile(myVirtualFile) : null;
    }
  }

  @Override
  public ProcessingItem @NotNull [] getProcessingItems(@NotNull CompileContext context) {
    Project project = context.getProject();
    if (project.isDefault() || !ValidationConfiguration.shouldValidate(this, project)) {
      return ProcessingItem.EMPTY_ARRAY;
    }
    ExcludesConfiguration excludesConfiguration = ValidationConfiguration.getExcludedEntriesConfiguration(project);
    List<ProcessingItem> items =
      DumbService.getInstance(project).runReadActionInSmartMode((Computable<List<ProcessingItem>>)() -> {
        CompileScope compileScope = context.getCompileScope();
        if (!myValidator.isAvailableOnScope(compileScope)) return null;

        ArrayList<ProcessingItem> items1 = new ArrayList<>();

        Processor<VirtualFile> processor = file -> {
          if (!file.isValid()) {
            return true;
          }

          if (myCompilerManager.isExcludedFromCompilation(file) ||
              excludesConfiguration.isExcluded(file)) {
            return true;
          }

          Module module = context.getModuleByFile(file);
          if (module != null) {
            PsiFile psiFile = myPsiManager.findFile(file);
            if (psiFile != null) {
              items1.add(new MyValidatorProcessingItem(psiFile));
            }
          }
          return true;
        };
        ContainerUtil.process(myValidator.getFilesToProcess(myPsiManager.getProject(), context), processor);
        return items1;
      });
    if (items == null) return ProcessingItem.EMPTY_ARRAY;

    return items.toArray(ProcessingItem.EMPTY_ARRAY);
  }

  @Override
  public ProcessingItem[] process(@NotNull CompileContext context, ProcessingItem @NotNull [] items) {
    context.getProgressIndicator().setText(myValidator.getProgressIndicatorText());

    Map<Class<?>, LocalInspectionTool> allTools = getInspectionToolMap(context.getProject(), myProfileManager);

    List<LocalInspectionTool> inspections = new ArrayList<>();
    for (Class<? extends LocalInspectionTool> aClass : myValidator.getInspectionToolClasses(context)) {
      LocalInspectionTool tool = allTools.get(aClass);
      if (tool != null) {
        inspections.add(tool);
      }
    }

    List<ProcessingItem> processedItems = new ArrayList<>();
    for (int i = 0; i < items.length; i++) {
      MyValidatorProcessingItem item = (MyValidatorProcessingItem)items[i];
      context.getProgressIndicator().checkCanceled();
      context.getProgressIndicator().setFraction((double)i / items.length);

      try {
        ourCompilationThreads.set(Boolean.TRUE);

        if (checkFile(inspections, item, context)) {
          processedItems.add(item);
        }
      }
      finally {
        ourCompilationThreads.set(Boolean.FALSE);
      }
    }

    return processedItems.toArray(ProcessingItem.EMPTY_ARRAY);
  }

  private static @NotNull Map<Class<?>, LocalInspectionTool> getInspectionToolMap(@NotNull Project project,
                                                                                  @NotNull InspectionProjectProfileManager profileManager) {
    Map<Class<?>, LocalInspectionTool> allTools = new IdentityHashMap<>();
    InspectionProfile profile = profileManager.getCurrentProfile();
    for (Tools tools : profile.getAllEnabledInspectionTools(project)) {
      InspectionProfileEntry inspectionProfileEntry = tools.getTool().getTool();
      if (inspectionProfileEntry instanceof LocalInspectionTool) {
        allTools.put(inspectionProfileEntry.getClass(), (LocalInspectionTool)inspectionProfileEntry);
      }
    }
    return allTools;
  }

  private boolean checkFile(List<? extends LocalInspectionTool> inspections, MyValidatorProcessingItem item, CompileContext context) {
    boolean hasErrors = !checkUnderReadAction(item, context, () -> myValidator.checkAdditionally(item.getPsiFile()));

    if (!checkUnderReadAction(item, context, () -> {
      PsiFile file = item.getPsiFile();
      if (file instanceof XmlFile) {
        return runXmlFileSchemaValidation((XmlFile)file);
      }
      return Collections.emptyMap();
    })) {
      hasErrors = true;
    }

    InspectionProfile inspectionProfile = myProfileManager.getCurrentProfile();
    for (LocalInspectionTool inspectionTool : inspections) {
      if (!checkUnderReadAction(item, context, () -> {
        PsiFile file = item.getPsiFile();
        if (file != null && getHighlightDisplayLevel(inspectionTool, inspectionProfile, file) != HighlightDisplayLevel.DO_NOT_SHOW) {
          return runInspectionTool(file, inspectionTool, getHighlightDisplayLevel(inspectionTool, inspectionProfile, file));
        }
        return Collections.emptyMap();
      })) {
        hasErrors = true;
      }
    }
    return !hasErrors;
  }

  @Override
  @NotNull
  public String getId() {
    return myValidator.getId();
  }

  private boolean checkUnderReadAction(@NotNull MyValidatorProcessingItem item,
                                       @NotNull CompileContext context,
                                       @NotNull Computable<? extends Map<ProblemDescriptor, HighlightDisplayLevel>> runnable) {
    return DumbService.getInstance(context.getProject()).runReadActionInSmartMode(() -> {
      PsiFile file = item.getPsiFile();
      if (file == null) return false;

      Document document = myPsiDocumentManager.getCachedDocument(file);
      if (document != null && myPsiDocumentManager.isUncommited(document)) {
        String url = file.getViewProvider().getVirtualFile().getUrl();
        context.addMessage(CompilerMessageCategory.WARNING, JavaCompilerBundle.message("warning.text.file.has.been.changed"), url, -1, -1);
        return false;
      }

      return !reportProblems(context, runnable.compute());
    });
  }

  private boolean reportProblems(CompileContext context, Map<ProblemDescriptor, HighlightDisplayLevel> problemsMap) {
    if (problemsMap.isEmpty()) {
      return false;
    }

    boolean errorsReported = false;
    for (Map.Entry<ProblemDescriptor, HighlightDisplayLevel> entry : problemsMap.entrySet()) {
      ProblemDescriptor problemDescriptor = entry.getKey();
      PsiElement element = problemDescriptor.getPsiElement();
      PsiFile psiFile = element.getContainingFile();
      if (psiFile == null) continue;

      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;

      CompilerMessageCategory category = myValidator.getCategoryByHighlightDisplayLevel(entry.getValue(), virtualFile, context);
      Document document = myPsiDocumentManager.getDocument(psiFile);

      int offset = problemDescriptor.getStartElement().getTextOffset();
      assert document != null;
      int line = document.getLineNumber(offset);
      int column = offset - document.getLineStartOffset(line);
      context.addMessage(category, problemDescriptor.getDescriptionTemplate(), virtualFile.getUrl(), line + 1, column + 1);
      if (CompilerMessageCategory.ERROR == category) {
        errorsReported = true;
      }
    }
    return errorsReported;
  }

  @NotNull
  private static Map<ProblemDescriptor, HighlightDisplayLevel> runInspectionTool(PsiFile file,
                                                                                 LocalInspectionTool inspectionTool,
                                                                                 HighlightDisplayLevel level) {
    Map<ProblemDescriptor, HighlightDisplayLevel> problemsMap = new LinkedHashMap<>();
    for (ProblemDescriptor descriptor : runInspectionOnFile(file, inspectionTool)) {
      ProblemHighlightType highlightType = descriptor.getHighlightType();

      HighlightDisplayLevel highlightDisplayLevel;
      if (highlightType == ProblemHighlightType.WEAK_WARNING) {
        highlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING;
      }
      else if (highlightType == ProblemHighlightType.INFORMATION) {
        highlightDisplayLevel = HighlightDisplayLevel.DO_NOT_SHOW;
      }
      else {
        highlightDisplayLevel = level;
      }
      problemsMap.put(descriptor, highlightDisplayLevel);
    }
    return problemsMap;
  }

  private static HighlightDisplayLevel getHighlightDisplayLevel(LocalInspectionTool inspectionTool,
                                                                InspectionProfile inspectionProfile, PsiElement file) {
    HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool.getShortName());
    return inspectionProfile.isToolEnabled(key, file) ? inspectionProfile.getErrorLevel(key, file) : HighlightDisplayLevel.DO_NOT_SHOW;
  }

  private Map<ProblemDescriptor, HighlightDisplayLevel> runXmlFileSchemaValidation(@NotNull XmlFile xmlFile) {
    AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSession(xmlFile), false);

    List<ExternalAnnotator<?,?>> annotators = ExternalLanguageAnnotators.allForFile(XMLLanguage.INSTANCE, xmlFile);
    for (ExternalAnnotator<?, ?> annotator : annotators) {
      processAnnotator(xmlFile, holder, annotator);
    }
    holder.assertAllAnnotationsCreated();

    if (!holder.hasAnnotations()) return Collections.emptyMap();

    Map<ProblemDescriptor, HighlightDisplayLevel> problemsMap = new LinkedHashMap<>();
    for (Annotation annotation : holder) {
      HighlightInfo info = HighlightInfo.fromAnnotation(annotation);
      if (info.getSeverity() == HighlightSeverity.INFORMATION) continue;

      PsiElement startElement = xmlFile.findElementAt(info.startOffset);
      PsiElement endElement = info.startOffset == info.endOffset ? startElement : xmlFile.findElementAt(info.endOffset - 1);
      if (startElement == null || endElement == null) continue;

      ProblemDescriptor descriptor =
        myInspectionManager.createProblemDescriptor(startElement, endElement, info.getDescription(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                    false);
      HighlightDisplayLevel level = info.getSeverity() == HighlightSeverity.ERROR? HighlightDisplayLevel.ERROR: HighlightDisplayLevel.WARNING;
      problemsMap.put(descriptor, level);
    }
    return problemsMap;
  }

  private static <X, Y> void processAnnotator(@NotNull XmlFile xmlFile, AnnotationHolderImpl holder, ExternalAnnotator<X, Y> annotator) {
    X initial = annotator.collectInformation(xmlFile);
    if (initial != null) {
      Y result = annotator.doAnnotate(initial);
      if (result != null) {
        holder.applyExternalAnnotatorWithContext(xmlFile, annotator, result);
      }
    }
  }

  @Override
  @NotNull
  @Nls
  public String getDescription() {
    return myValidator.getDescription();
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return PsiElementsValidityState.load(in);
  }
}
