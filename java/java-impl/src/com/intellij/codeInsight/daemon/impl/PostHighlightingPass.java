/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.*;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedParameters.UnusedParametersInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.*;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomNamedTarget;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.changeSignature.ChangeSignatureGestureDetector;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;

import static com.intellij.psi.search.PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
import static com.intellij.psi.search.PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES;

public class PostHighlightingPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PostHighlightingPass");
  private RefCountHolder myRefCountHolder;
  private final PsiFile myFile;
  @Nullable private final Editor myEditor;
  private final int myStartOffset;
  private final int myEndOffset;

  private Collection<HighlightInfo> myHighlights;
  private boolean myHasRedundantImports;
  private final JavaCodeStyleManager myStyleManager;
  private int myCurrentEntryIndex;
  private boolean myHasMissortedImports;
  private static final ImplicitUsageProvider[] ourImplicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
  private UnusedDeclarationInspection myDeadCodeInspection;
  private UnusedSymbolLocalInspection myUnusedSymbolInspection;
  private HighlightDisplayKey myUnusedSymbolKey;
  private boolean myDeadCodeEnabled;
  private boolean myInLibrary;
  private HighlightDisplayKey myDeadCodeKey;
  private HighlightInfoType myDeadCodeInfoType;
  private UnusedParametersInspection myUnusedParametersInspection;

  PostHighlightingPass(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull Document document,
                       @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(project, document, "Unused symbols", file, editor, file.getTextRange(), true, highlightInfoProcessor);
    myFile = file;
    myEditor = editor;
    myStartOffset = 0;
    myEndOffset = file.getTextLength();

    myStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myCurrentEntryIndex = -1;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull final ProgressIndicator progress) {
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    final FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
    final List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    final Set<PsiElement> elementSet = new THashSet<PsiElement>();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightingLevelManager.getInstance(myProject).shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      elementSet.addAll(elements);
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    myInLibrary = fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile);

    myRefCountHolder = RefCountHolder.endUsing(myFile, progress);
    if (myRefCountHolder == null || !myRefCountHolder.retrieveUnusedReferencesInfo(progress, new Runnable() {
      @Override
      public void run() {
        boolean errorFound = collectHighlights(elementSet, highlights, progress);
        myHighlights = highlights;
        if (errorFound) {
          fileStatusMap.setErrorFoundFlag(myDocument, true);
        }
      }
    })) {
      // we must be sure GHP will restart
      fileStatusMap.markFileScopeDirty(getDocument(), Pass.UPDATE_ALL);
      GeneralHighlightingPass.cancelAndRestartDaemonLater(progress, myProject, this);
    }
  }

  @Override
  public List<HighlightInfo> getInfos() {
    return myHighlights == null ? null : new ArrayList<HighlightInfo>(myHighlights);
  }

  @Override
  protected void applyInformationWithProgress() {
    if (myHighlights == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myHighlights, getColorsScheme(), Pass.POST_UPDATE_ALL);
    PostHighlightingPassFactory.markFileUpToDate(myFile);

    Editor editor = myEditor;
    if (editor != null && timeToOptimizeImports()) {
      optimizeImportsOnTheFly(editor);
    }
  }

  private void optimizeImportsOnTheFly(@NotNull final Editor editor) {
    if (myHasRedundantImports || myHasMissortedImports) {
      final OptimizeImportsFix optimizeImportsFix = new OptimizeImportsFix();
      if (optimizeImportsFix.isAvailable(myProject, editor, myFile) && myFile.isWritable()) {
        invokeOnTheFlyImportOptimizer(new Runnable() {
          @Override
          public void run() {
            optimizeImportsFix.invoke(myProject, editor, myFile);
          }
        }, myFile, editor);
      }
    }
  }

  public static void invokeOnTheFlyImportOptimizer(@NotNull final Runnable runnable,
                                                   @NotNull final PsiFile file,
                                                   @NotNull final Editor editor) {
    final long stamp = editor.getDocument().getModificationStamp();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (file.getProject().isDisposed() || editor.isDisposed() || editor.getDocument().getModificationStamp() != stamp) return;
        //no need to optimize imports on the fly during undo/redo
        final UndoManager undoManager = UndoManager.getInstance(editor.getProject());
        if (undoManager.isUndoInProgress() || undoManager.isRedoInProgress()) return;
        PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
        String beforeText = file.getText();
        final long oldStamp = editor.getDocument().getModificationStamp();
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(runnable);
          }
        });
        if (oldStamp != editor.getDocument().getModificationStamp()) {
          String afterText = file.getText();
          if (Comparing.strEqual(beforeText, afterText)) {
            LOG.error(LogMessageEx.createEvent("Import optimizer  hasn't optimized any imports", file.getViewProvider().getVirtualFile().getPath(),
                                               AttachmentFactory.createAttachment(file.getViewProvider().getVirtualFile())));
          }
        }
      }
    });
  }

  // returns true if error highlight was created
  private boolean collectHighlights(@NotNull Collection<PsiElement> elements,
                                    @NotNull final List<HighlightInfo> result,
                                    @NotNull ProgressIndicator progress) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    myUnusedSymbolKey = HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME);
    boolean unusedSymbolEnabled = profile.isToolEnabled(myUnusedSymbolKey, myFile);
    HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME);
    boolean unusedImportEnabled = profile.isToolEnabled(unusedImportKey, myFile);
    myUnusedSymbolInspection = (UnusedSymbolLocalInspection)profile.getUnwrappedTool(UnusedSymbolLocalInspection.SHORT_NAME, myFile);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || myUnusedSymbolInspection != null);

    myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspection.SHORT_NAME);
    myDeadCodeInspection = (UnusedDeclarationInspection)profile.getUnwrappedTool(UnusedDeclarationInspection.SHORT_NAME, myFile);
    myDeadCodeEnabled = profile.isToolEnabled(myDeadCodeKey, myFile);

    myUnusedParametersInspection = (UnusedParametersInspection)profile.getUnwrappedTool(UnusedParametersInspection.SHORT_NAME, myFile);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || myUnusedParametersInspection != null);
    if (unusedImportEnabled && JspPsiUtil.isInJspFile(myFile)) {
      final JspFile jspFile = JspPsiUtil.getJspFile(myFile);
      if (jspFile != null) {
        unusedImportEnabled = !JspSpiUtil.isIncludedOrIncludesSomething(jspFile);
      }
    }

    myDeadCodeInfoType = myDeadCodeKey == null
                         ? null
                         : new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(myDeadCodeKey, myFile).getSeverity(),
                                                                       HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());

    GlobalUsageHelper helper = new GlobalUsageHelper() {
      @Override
      public boolean shouldCheckUsages(@NotNull PsiMember member) {
        return !myInLibrary && myDeadCodeEnabled && !myDeadCodeInspection.isEntryPoint(member);
      }

      @Override
      public boolean isCurrentFileAlreadyChecked() {
        return true;
      }

      @Override
      public boolean isLocallyUsed(@NotNull PsiNamedElement member) {
        return myRefCountHolder.isReferenced(member);
      }
    };

    boolean errorFound = false;
    if (unusedSymbolEnabled) {
      for (PsiElement element : elements) {
        progress.checkCanceled();
        if (element instanceof PsiIdentifier) {
          PsiIdentifier identifier = (PsiIdentifier)element;
          HighlightInfo info = processIdentifier(identifier, progress, helper);
          if (info != null) {
            errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
            result.add(info);
          }
        }
      }
    }
    if (unusedImportEnabled && myFile instanceof PsiJavaFile && HighlightingLevelManager.getInstance(myProject).shouldHighlight(myFile)) {
      PsiImportList importList = ((PsiJavaFile)myFile).getImportList();
      if (importList != null) {
        final PsiImportStatementBase[] imports = importList.getAllImportStatements();
        for (PsiImportStatementBase statement : imports) {
          progress.checkCanceled();
          final HighlightInfo info = processImport(statement, unusedImportKey);
          if (info != null) {
            errorFound |= info.getSeverity() == HighlightSeverity.ERROR;
            result.add(info);
          }
        }
      }
    }

    return errorFound;
  }

  @Nullable
  private HighlightInfo processIdentifier(PsiIdentifier identifier, ProgressIndicator progress, GlobalUsageHelper helper) {
    if (SuppressionUtil.inspectionResultSuppressed(identifier, myUnusedSymbolInspection)) return null;
    PsiElement parent = identifier.getParent();
    if (PsiUtilCore.hasErrorElementChild(parent)) return null;

    if (parent instanceof PsiLocalVariable && myUnusedSymbolInspection.LOCAL_VARIABLE) {
      return processLocalVariable((PsiLocalVariable)parent, identifier, progress);
    }
    if (parent instanceof PsiField && myUnusedSymbolInspection.FIELD) {
      return processField((PsiField)parent, identifier, progress, helper);
    }
    if (parent instanceof PsiParameter && myUnusedSymbolInspection.PARAMETER) {
      if (SuppressionUtil.isSuppressed(identifier, UnusedParametersInspection.SHORT_NAME)) return null;
      return processParameter((PsiParameter)parent, identifier, progress);
    }
    if (parent instanceof PsiMethod && myUnusedSymbolInspection.METHOD) {
      return processMethod((PsiMethod)parent, identifier, progress, helper);
    }
    if (parent instanceof PsiClass && myUnusedSymbolInspection.CLASS) {
      return processClass((PsiClass)parent, identifier, progress, helper);
    }
    return null;
  }

  @Nullable
  private HighlightInfo processLocalVariable(@NotNull PsiLocalVariable variable,
                                             @NotNull PsiIdentifier identifier,
                                             @NotNull ProgressIndicator progress) {
    if (variable instanceof PsiResourceVariable && PsiUtil.isIgnoredName(variable.getName())) return null;
    if (isImplicitUsage(variable, progress)) return null;

    if (!myRefCountHolder.isReferenced(variable)) {
      String message = JavaErrorMessages.message("local.variable.is.never.used", identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
      IntentionAction fix = variable instanceof PsiResourceVariable ? new RenameToIgnoredFix(variable) : new RemoveUnusedVariableFix(variable);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix, myUnusedSymbolKey);
      return highlightInfo;
    }

    boolean referenced = myRefCountHolder.isReferencedForRead(variable);
    if (!referenced && !isImplicitRead(variable, progress)) {
      String message = JavaErrorMessages.message("local.variable.is.not.used.for.reading", identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), myUnusedSymbolKey);
      return highlightInfo;
    }

    if (!variable.hasInitializer()) {
      referenced = myRefCountHolder.isReferencedForWrite(variable);
      if (!referenced && !isImplicitWrite(variable, progress)) {
        String message = JavaErrorMessages.message("local.variable.is.not.assigned", identifier.getText());
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(UnusedSymbolLocalInspection.DISPLAY_NAME), myUnusedSymbolKey);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  public static boolean isImplicitUsage(final PsiModifierListOwner element, ProgressIndicator progress) {
    if (UnusedSymbolLocalInspection.isInjected(element)) return true;
    for (ImplicitUsageProvider provider : ourImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitUsage(element)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isImplicitRead(final PsiVariable element, ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: ourImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitRead(element)) {
        return true;
      }
    }
    return UnusedSymbolLocalInspection.isInjected(element);
  }

  private static boolean isImplicitWrite(final PsiVariable element, ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: ourImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitWrite(element)) {
        return true;
      }
    }
    return UnusedSymbolLocalInspection.isInjected(element);
  }

  @Nullable 
  public static HighlightInfo createUnusedSymbolInfo(@NotNull PsiElement element, @NotNull String message, @NotNull final HighlightInfoType highlightInfoType) {
    HighlightInfo info = HighlightInfo.newHighlightInfo(highlightInfoType).range(element).descriptionAndTooltip(message).create();
    if (info == null) {
      return null; //filtered out
    }
    
    UnusedDeclarationFixProvider[] fixProviders = Extensions.getExtensions(UnusedDeclarationFixProvider.EP_NAME);
    for (UnusedDeclarationFixProvider provider : fixProviders) {
      IntentionAction[] fixes = provider.getQuickFixes(element);
      for (IntentionAction fix : fixes) {
        QuickFixAction.registerQuickFixAction(info, fix);
      }
    }
    return info;
  }

  @Nullable
  private HighlightInfo processField(@NotNull final PsiField field,
                                     @NotNull PsiIdentifier identifier,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull GlobalUsageHelper helper) {
    if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
      return null;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(field) && !isImplicitUsage(field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.used", identifier.getText());

        HighlightInfo highlightInfo = suggestionsToMakeFieldUsed(field, identifier, message);
        if (!field.hasInitializer()) {
          QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), new CreateConstructorParameterFromFieldFix(field));
        }
        return highlightInfo;
      }

      final boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced && !isImplicitRead(field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.used.for.reading", identifier.getText());
        return suggestionsToMakeFieldUsed(field, identifier, message);
      }

      if (field.hasInitializer()) {
        return null;
      }
      final boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
      if (!writeReferenced && !isImplicitWrite(field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.assigned", identifier.getText());
        final HighlightInfo info = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);

        QuickFixAction.registerQuickFixAction(info, new CreateGetterOrSetterFix(false, true, field), myUnusedSymbolKey);
        QuickFixAction.registerQuickFixAction(info, HighlightMethodUtil.getFixRange(field), new CreateConstructorParameterFromFieldFix(field));
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, new Processor<String>() {
          @Override
          public boolean process(final String annoName) {
            QuickFixAction.registerQuickFixAction(info, UnusedSymbolLocalInspection.createQuickFix(annoName, "fields", field.getProject()));
            return true;
          }
        });
        return info;
      }
    }
    else if (isImplicitUsage(field, progress)) {
      return null;
    }
    else if (isFieldUnused(field, progress, helper)) {
      return formatUnusedSymbolHighlightInfo("field.is.not.used", field, "fields", myDeadCodeKey, myDeadCodeInfoType, identifier);
    }
    return null;
  }

  public static boolean isFieldUnused(PsiField field, ProgressIndicator progress, GlobalUsageHelper helper) {
    if (helper.isLocallyUsed(field) || !weAreSureThereAreNoUsages(field, progress, helper)) {
      return false;
    }
    return !(field instanceof PsiEnumConstant) || !isEnumValuesMethodUsed(field, progress, helper);
  }

  private HighlightInfo suggestionsToMakeFieldUsed(final PsiField field, final PsiIdentifier identifier, final String message) {
    HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
    QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), myUnusedSymbolKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterFix(true, false, field), myUnusedSymbolKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterFix(false, true, field), myUnusedSymbolKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterFix(true, true, field), myUnusedSymbolKey);
    return highlightInfo;
  }

  private static boolean isOverriddenOrOverrides(PsiMethod method) {
    boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
  }

  @Nullable
  private HighlightInfo processParameter(@NotNull PsiParameter parameter,
                                         @NotNull PsiIdentifier identifier,
                                         @NotNull ProgressIndicator progress) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
      if (PsiUtilCore.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() ||
           method.hasModifierProperty(PsiModifier.PRIVATE) ||
           method.hasModifierProperty(PsiModifier.STATIC) ||
           !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
           myUnusedSymbolInspection.REPORT_PARAMETER_FOR_PUBLIC_METHODS &&
           !isOverriddenOrOverrides(method)) &&
          !method.hasModifierProperty(PsiModifier.NATIVE) &&
          !JavaHighlightUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
          !PsiClassImplUtil.isMainOrPremainMethod(method)) {
        if (UnusedSymbolLocalInspection.isInjected(method)) return null;
        HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
        if (highlightInfo != null) {
          List<IntentionAction> options = new ArrayList<IntentionAction>();
          options.addAll(IntentionManager.getInstance().getStandardIntentionOptions(myUnusedSymbolKey, myFile));
          if (myUnusedParametersInspection != null) {
            SuppressQuickFix[] batchSuppressActions = myUnusedParametersInspection.getBatchSuppressActions(parameter);
            Collections.addAll(options, SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions));
          }
          //need suppress from Unused Parameters but settings from Unused Symbol
          QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedParameterFix(parameter),
                                                options, HighlightDisplayKey.getDisplayNameByKey(myUnusedSymbolKey));
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement && !PsiUtil.isIgnoredName(parameter.getName())) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new RenameToIgnoredFix(parameter), myUnusedSymbolKey);
        return highlightInfo;
      }
    }

    return null;
  }

  @Nullable
  private HighlightInfo checkUnusedParameter(@NotNull PsiParameter parameter,
                                             @NotNull PsiIdentifier identifier,
                                             @NotNull ProgressIndicator progress) {
    if (!myRefCountHolder.isReferenced(parameter) && !isImplicitUsage(parameter, progress)) {
      String message = JavaErrorMessages.message("parameter.is.not.used", identifier.getText());
      return createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
    }
    return null;
  }

  @Nullable
  private HighlightInfo processMethod(@NotNull final PsiMethod method,
                                      @NotNull PsiIdentifier identifier,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull GlobalUsageHelper helper) {
    if (isMethodReferenced(method, progress, helper)) return null;
    HighlightInfoType highlightInfoType;
    HighlightDisplayKey highlightDisplayKey;
    String key;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      highlightInfoType = HighlightInfoType.UNUSED_SYMBOL;
      highlightDisplayKey = myUnusedSymbolKey;
      key = method.isConstructor() ? "private.constructor.is.not.used" : "private.method.is.not.used";
    }
    else {
      highlightInfoType = myDeadCodeInfoType;
      highlightDisplayKey = myDeadCodeKey;
      key = method.isConstructor() ? "constructor.is.not.used" : "method.is.not.used";
    }
    String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
    String message = JavaErrorMessages.message(key, symbolName);
    final HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, highlightInfoType);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(method), highlightDisplayKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(method, new Processor<String>() {
      @Override
      public boolean process(final String annoName) {
        QuickFixAction
          .registerQuickFixAction(highlightInfo, UnusedSymbolLocalInspection.createQuickFix(annoName, "methods", method.getProject()));
        return true;
      }
    });
    PsiClass containingClass = method.getContainingClass();
    if (method.getReturnType() != null || containingClass != null && Comparing.strEqual(containingClass.getName(), method.getName())) {
      //ignore methods with deleted return types as they are always marked as unused without any reason
      ChangeSignatureGestureDetector.getInstance(myProject).dismissForElement(method);
    }
    return highlightInfo;
  }

  public static boolean isMethodReferenced(PsiMethod method,
                                            ProgressIndicator progress,
                                            GlobalUsageHelper helper) {
    if (helper.isLocallyUsed(method)) return true;

    boolean aPrivate = method.hasModifierProperty(PsiModifier.PRIVATE);
    PsiClass containingClass = method.getContainingClass();
    if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return true;
    if (aPrivate) {
      if (isIntentionalPrivateConstructor(method, containingClass)) {
        return true;
      }
      if (isImplicitUsage(method, progress)) {
        return true;
      }
      if (!helper.isCurrentFileAlreadyChecked()) {
        return !weAreSureThereAreNoUsages(method, progress, helper);
      }
    }
    else {
      //class maybe used in some weird way, e.g. from XML, therefore the only constructor is used too
      if (containingClass != null && method.isConstructor()
          && containingClass.getConstructors().length == 1
          && isClassUsed(containingClass, progress, helper)) {
        return true;
      }
      if (isImplicitUsage(method, progress)) return true;

      if (method.findSuperMethods().length != 0) {
        return true;
      }
      if (!weAreSureThereAreNoUsages(method, progress, helper)) {
        return true;
      }
    }
    return false;
  }

  private static boolean weAreSureThereAreNoUsages(@NotNull PsiMember member, ProgressIndicator progress, GlobalUsageHelper helper) {
    if (!helper.shouldCheckUsages(member)) return false;

    String name = member.getName();
    if (name == null) return false;
    SearchScope useScope = member.getUseScope();
    Project project = member.getProject();
    if (useScope instanceof GlobalSearchScope) {
      // some classes may have references from within XML outside dependent modules, e.g. our actions
      if (member instanceof PsiClass) {
        useScope = GlobalSearchScope.projectScope(project).uniteWith((GlobalSearchScope)useScope);
      }

      PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(project);
      PsiFile file = member.getContainingFile();
      PsiFile ignoreFile = helper.isCurrentFileAlreadyChecked() ? file : null;
      PsiSearchHelper.SearchCostResult cheapEnough = searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, ignoreFile, progress);
      if (cheapEnough == TOO_MANY_OCCURRENCES) return false;

      //search usages if it cheap
      //if count is 0 there is no usages since we've called myRefCountHolder.isReferenced() before
      if (cheapEnough == ZERO_OCCURRENCES) {
        if (!canBeReferencedViaWeirdNames(member)) return true;
      }

      if (member instanceof PsiMethod) {
        String propertyName = PropertyUtil.getPropertyName(member);
        if (propertyName != null && file != null) {
          SearchScope fileScope = file.getUseScope();
          if (fileScope instanceof GlobalSearchScope &&
              searchHelper.isCheapEnoughToSearch(propertyName, (GlobalSearchScope)fileScope, ignoreFile, progress) == TOO_MANY_OCCURRENCES) {
            return false;
          }
        }
      }
    }
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandler handler = new JavaFindUsagesHandler(member, new JavaFindUsagesHandlerFactory(project));
    FindUsagesOptions findUsagesOptions = handler.getFindUsagesOptions().clone();
    findUsagesOptions.searchScope = useScope;
    findUsagesOptions.isSearchForTextOccurrences = true;
    return !findUsagesManager.isUsed(member, findUsagesOptions);
  }

  private static boolean isEnumValuesMethodUsed(PsiMember member, ProgressIndicator progress, GlobalUsageHelper helper) {
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass == null || !(containingClass instanceof PsiClassImpl)) return true;
    final PsiMethod valuesMethod = ((PsiClassImpl)containingClass).getValuesMethod();
    return valuesMethod == null || isMethodReferenced(valuesMethod, progress, helper);
  }

  private static boolean canBeReferencedViaWeirdNames(PsiMember member) {
    if (member instanceof PsiClass) return false;
    PsiFile containingFile = member.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) return true;  // Groovy field can be referenced from Java by getter
    if (member instanceof PsiField) return false;  //Java field cannot be referenced by anything but its name
    if (member instanceof PsiMethod) {
      return PropertyUtil.isSimplePropertyAccessor((PsiMethod)member);  //Java accessors can be referenced by field name from Groovy
    }
    return false;
  }

  @Nullable
  private HighlightInfo processClass(@NotNull PsiClass aClass,
                                     @NotNull PsiIdentifier identifier,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull GlobalUsageHelper helper) {
    if (isClassUsed(aClass, progress, helper)) return null;

    String pattern;
    HighlightDisplayKey highlightDisplayKey;
    HighlightInfoType highlightInfoType;
    if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      pattern = aClass.isInterface()
                       ? "private.inner.interface.is.not.used"
                       : "private.inner.class.is.not.used";
      highlightDisplayKey = myUnusedSymbolKey;
      highlightInfoType = HighlightInfoType.UNUSED_SYMBOL;
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
      pattern = "local.class.is.not.used";
      highlightDisplayKey = myUnusedSymbolKey;
      highlightInfoType = HighlightInfoType.UNUSED_SYMBOL;
    }
    else if (aClass instanceof PsiTypeParameter) {
      pattern = "type.parameter.is.not.used";
      highlightDisplayKey = myUnusedSymbolKey;
      highlightInfoType = HighlightInfoType.UNUSED_SYMBOL;
    }
    else {
      pattern = "class.is.not.used";
      highlightDisplayKey = myDeadCodeKey;
      highlightInfoType = myDeadCodeInfoType;
    }
    return formatUnusedSymbolHighlightInfo(pattern, aClass, "classes", highlightDisplayKey, highlightInfoType, identifier);
  }

  public static boolean isClassUsed(PsiClass aClass, ProgressIndicator progress, GlobalUsageHelper helper) {
    if (aClass == null) return true;
    Boolean result = helper.unusedClassCache.get(aClass);
    if (result == null) {
      result = isReallyUsed(aClass, progress, helper);
      helper.unusedClassCache.put(aClass, result);
    }
    return result;
  }

  private static boolean isReallyUsed(PsiClass aClass, ProgressIndicator progress, GlobalUsageHelper helper) {
    if (isImplicitUsage(aClass, progress) || helper.isLocallyUsed(aClass)) return true;
    if (helper.isCurrentFileAlreadyChecked()) {
      if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE) ||
             aClass.getParent() instanceof PsiDeclarationStatement ||
             aClass instanceof PsiTypeParameter) return false;
    }
    return !weAreSureThereAreNoUsages(aClass, progress, helper);
  }

  private static HighlightInfo formatUnusedSymbolHighlightInfo(@NotNull @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String pattern,
                                                               @NotNull final PsiNameIdentifierOwner aClass,
                                                               @NotNull final String element,
                                                               @NotNull HighlightDisplayKey highlightDisplayKey,
                                                               @NotNull HighlightInfoType highlightInfoType,
                                                               @NotNull PsiElement identifier) {
    String symbolName = aClass.getName();
    String message = JavaErrorMessages.message(pattern, symbolName);
    final HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, highlightInfoType);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(aClass), highlightDisplayKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes((PsiModifierListOwner)aClass, new Processor<String>() {
      @Override
      public boolean process(final String annoName) {
        QuickFixAction
          .registerQuickFixAction(highlightInfo, UnusedSymbolLocalInspection.createQuickFix(annoName, element, aClass.getProject()));
        return true;
      }
    });
    return highlightInfo;
  }

  @Nullable
  private HighlightInfo processImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    // jsp include directive hack
    if (importStatement instanceof JspxImportStatement && ((JspxImportStatement)importStatement).isForeignFileImport()) return null;

    if (PsiUtilCore.hasErrorElementChild(importStatement)) return null;

    boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
    if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
      //check import from same package
      String packageName = ((PsiClassOwner)importStatement.getContainingFile()).getPackageName();
      PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
      PsiElement resolved = reference == null ? null : reference.resolve();
      if (resolved instanceof PsiPackage) {
        isRedundant = packageName.equals(((PsiQualifiedNamedElement)resolved).getQualifiedName());
      }
      else if (resolved instanceof PsiClass && !importStatement.isOnDemand()) {
        String qName = ((PsiClass)resolved).getQualifiedName();
        if (qName != null) {
          String name = ((PomNamedTarget)resolved).getName();
          isRedundant = qName.equals(packageName + '.' + name);
        }
      }
    }

    if (isRedundant) {
      return registerRedundantImport(importStatement, unusedImportKey);
    }

    int entryIndex = myStyleManager.findEntryIndex(importStatement);
    if (entryIndex < myCurrentEntryIndex) {
      myHasMissortedImports = true;
    }
    myCurrentEntryIndex = entryIndex;

    return null;
  }

  private HighlightInfo registerRedundantImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    String description = InspectionsBundle.message("unused.import.statement");
    HighlightInfo info =
      HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.UNUSED_IMPORT).range(importStatement).descriptionAndTooltip(description)
        .create();

    QuickFixAction.registerQuickFixAction(info, new OptimizeImportsFix(), unusedImportKey);
    QuickFixAction.registerQuickFixAction(info, new EnableOptimizeImportsOnTheFlyFix(), unusedImportKey);
    myHasRedundantImports = true;
    return info;
  }

  private boolean timeToOptimizeImports() {
    if (!CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) return false;

    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    // dont optimize out imports in JSP since it can be included in other JSP
    if (file == null || !codeAnalyzer.isHighlightingAvailable(file) || !(file instanceof PsiJavaFile) || file instanceof ServerPageFile) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false;
    boolean errors = containsErrorsPreventingOptimize(file);

    return !errors && DaemonListeners.canChangeFileSilently(myFile);
  }

  private boolean containsErrorsPreventingOptimize(@NotNull PsiFile file) {
    // ignore unresolved imports errors
    PsiImportList importList = ((PsiJavaFile)file).getImportList();
    final TextRange importsRange = importList == null ? TextRange.EMPTY_RANGE : importList.getTextRange();
    boolean hasErrorsExceptUnresolvedImports = !DaemonCodeAnalyzerEx
      .processHighlights(myDocument, myProject, HighlightSeverity.ERROR, 0, myDocument.getTextLength(), new Processor<HighlightInfo>() {
        @Override
        public boolean process(HighlightInfo error) {
          int infoStart = error.getActualStartOffset();
          int infoEnd = error.getActualEndOffset();

          return importsRange.containsRange(infoStart, infoEnd) && error.type.equals(HighlightInfoType.WRONG_REF);
        }
      });

    return hasErrorsExceptUnresolvedImports;
  }

  private static boolean isIntentionalPrivateConstructor(@NotNull PsiMethod method, PsiClass containingClass) {
    return method.isConstructor() &&
           method.getParameterList().getParametersCount() == 0 &&
           containingClass != null &&
           containingClass.getConstructors().length == 1;
  }
}
