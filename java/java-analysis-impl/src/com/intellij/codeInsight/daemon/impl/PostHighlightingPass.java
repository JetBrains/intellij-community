/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInsight.daemon.impl.analysis.*;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomNamedTarget;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.containers.Predicate;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PostHighlightingPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PostHighlightingPass");
  private static final Key<Long> LAST_POST_PASS_TIMESTAMP = Key.create("LAST_POST_PASS_TIMESTAMP");
  private RefCountHolder myRefCountHolder;
  private final PsiFile myFile;
  @Nullable private final Editor myEditor;
  private final boolean myUnusedImportEnabled;
  @NotNull private final Predicate<PsiElement> myIsEntryPointPredicate;
  private final int myStartOffset;
  private final int myEndOffset;

  private Collection<HighlightInfo> myHighlights;
  private boolean myHasRedundantImports;
  private int myCurrentEntryIndex;
  private boolean myHasMissortedImports;
  private static final ImplicitUsageProvider[] ourImplicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
  private UnusedSymbolLocalInspectionBase myUnusedSymbolInspection;
  private HighlightDisplayKey myUnusedSymbolKey;
  private boolean myInLibrary;
  private HighlightDisplayKey myDeadCodeKey;
  private HighlightInfoType myDeadCodeInfoType;

  public PostHighlightingPass(@NotNull Project project,
                              @NotNull PsiFile file,
                              @Nullable Editor editor,
                              @NotNull Document document,
                              @NotNull HighlightInfoProcessor highlightInfoProcessor,
                              boolean unusedImportEnabled,
                              @NotNull Predicate<PsiElement> isEntryPoint) {
    super(project, document, "Unused symbols", file, editor, file.getTextRange(), true, highlightInfoProcessor);
    myFile = file;
    myEditor = editor;
    myUnusedImportEnabled = unusedImportEnabled;
    myIsEntryPointPredicate = isEntryPoint;
    myStartOffset = 0;
    myEndOffset = file.getTextLength();

    myCurrentEntryIndex = -1;
  }

  static boolean isUpToDate(@NotNull PsiFile file) {
    Long lastStamp = file.getUserData(LAST_POST_PASS_TIMESTAMP);
    long currentStamp = PsiModificationTracker.SERVICE.getInstance(file.getProject()).getModificationCount();
    return lastStamp != null && lastStamp == currentStamp || !ProblemHighlightFilter.shouldHighlightFile(file);
  }

  private static void markFileUpToDate(@NotNull PsiFile file) {
    long lastStamp = PsiModificationTracker.SERVICE.getInstance(file.getProject()).getModificationCount();
    file.putUserData(LAST_POST_PASS_TIMESTAMP, lastStamp);
  }

  private static boolean isInjected(@NotNull Project project, @NotNull PsiModifierListOwner modifierListOwner) {
    return EntryPointsManagerBase.getInstance(project).isEntryPoint(modifierListOwner);
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
    markFileUpToDate(myFile);

    Editor editor = myEditor;
    if (editor != null) {
      optimizeImportsOnTheFly(editor);
    }
  }

  private void optimizeImportsOnTheFly(@NotNull final Editor editor) {
    if (myHasRedundantImports || myHasMissortedImports) {
      IntentionAction optimizeImportsFix = QuickFixFactory.getInstance().createOptimizeImportsFix();
      if (optimizeImportsFix.isAvailable(myProject, editor, myFile) && myFile.isWritable()) {
        optimizeImportsFix.invoke(myProject, editor, myFile);
      }
    }
  }

  // returns true if error highlight was created
  private boolean collectHighlights(@NotNull Collection<PsiElement> elements,
                                    @NotNull final List<HighlightInfo> result,
                                    @NotNull ProgressIndicator progress) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    myUnusedSymbolKey = HighlightDisplayKey.find(UnusedSymbolLocalInspectionBase.SHORT_NAME);
    boolean unusedSymbolEnabled = profile.isToolEnabled(myUnusedSymbolKey, myFile);
    myUnusedSymbolInspection = (UnusedSymbolLocalInspectionBase)profile.getUnwrappedTool(UnusedSymbolLocalInspectionBase.SHORT_NAME, myFile);
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || myUnusedSymbolInspection != null);

    myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspection.SHORT_NAME);

    HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME);

    myDeadCodeInfoType = myDeadCodeKey == null
                         ? HighlightInfoType.UNUSED_SYMBOL
                         : new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(myDeadCodeKey, myFile).getSeverity(),
                                                                       HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());

    GlobalUsageHelper helper = new GlobalUsageHelper() {
      @Override
      public boolean shouldCheckUsages(@NotNull PsiMember member) {
        return !myInLibrary && !myIsEntryPointPredicate.apply(member);
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
    if (myUnusedImportEnabled && myFile instanceof PsiJavaFile && HighlightingLevelManager.getInstance(myProject).shouldHighlight(myFile)) {
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
  private HighlightInfo processIdentifier(@NotNull PsiIdentifier identifier, @NotNull ProgressIndicator progress, @NotNull GlobalUsageHelper helper) {
    if (SuppressionUtil.inspectionResultSuppressed(identifier, myUnusedSymbolInspection)) return null;
    PsiElement parent = identifier.getParent();
    if (PsiUtilCore.hasErrorElementChild(parent)) return null;

    if (parent instanceof PsiLocalVariable && myUnusedSymbolInspection.LOCAL_VARIABLE) {
      return processLocalVariable((PsiLocalVariable)parent, identifier, progress);
    }
    if (parent instanceof PsiField && myUnusedSymbolInspection.FIELD) {
      return processField(myProject, (PsiField)parent, identifier, progress, helper);
    }
    if (parent instanceof PsiParameter && myUnusedSymbolInspection.PARAMETER) {
      if (SuppressionUtil.isSuppressed(identifier, UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME)) return null;
      return processParameter(myProject, (PsiParameter)parent, identifier, progress);
    }
    if (parent instanceof PsiMethod && myUnusedSymbolInspection.METHOD) {
      return processMethod(myProject, (PsiMethod)parent, identifier, progress, helper);
    }
    if (parent instanceof PsiClass && myUnusedSymbolInspection.CLASS) {
      return processClass(myProject, (PsiClass)parent, identifier, progress, helper);
    }
    return null;
  }

  @Nullable
  private HighlightInfo processLocalVariable(@NotNull PsiLocalVariable variable,
                                             @NotNull PsiIdentifier identifier,
                                             @NotNull ProgressIndicator progress) {
    if (variable instanceof PsiResourceVariable && PsiUtil.isIgnoredName(variable.getName())) return null;
    if (isImplicitUsage(myProject, variable, progress)) return null;

    if (!myRefCountHolder.isReferenced(variable)) {
      String message = JavaErrorMessages.message("local.variable.is.never.used", identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
      IntentionAction fix = variable instanceof PsiResourceVariable ? QuickFixFactory.getInstance().createRenameToIgnoredFix(variable) : QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable);
      QuickFixAction.registerQuickFixAction(highlightInfo, fix, myUnusedSymbolKey);
      return highlightInfo;
    }

    boolean referenced = myRefCountHolder.isReferencedForRead(variable);
    if (!referenced && !isImplicitRead(myProject, variable, progress)) {
      String message = JavaErrorMessages.message("local.variable.is.not.used.for.reading", identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
      QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createRemoveUnusedVariableFix(variable), myUnusedSymbolKey);
      return highlightInfo;
    }

    if (!variable.hasInitializer()) {
      referenced = myRefCountHolder.isReferencedForWrite(variable);
      if (!referenced && !isImplicitWrite(myProject, variable, progress)) {
        String message = JavaErrorMessages.message("local.variable.is.not.assigned", identifier.getText());
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(UnusedSymbolLocalInspectionBase.DISPLAY_NAME), myUnusedSymbolKey);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  public static boolean isImplicitUsage(@NotNull Project project,
                                        @NotNull PsiModifierListOwner element,
                                        @NotNull ProgressIndicator progress) {
    if (isInjected(project, element)) return true;
    for (ImplicitUsageProvider provider : ourImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitUsage(element)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isImplicitRead(@NotNull Project project, @NotNull PsiVariable element, @NotNull ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: ourImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitRead(element)) {
        return true;
      }
    }
    return isInjected(project, element);
  }

  private static boolean isImplicitWrite(@NotNull Project project,
                                         @NotNull PsiVariable element,
                                         @NotNull ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: ourImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitWrite(element)) {
        return true;
      }
    }
    return isInjected(project, element);
  }

  @Nullable
  public static HighlightInfo createUnusedSymbolInfo(@NotNull PsiElement element,
                                                     @NotNull String message,
                                                     @NotNull final HighlightInfoType highlightInfoType) {
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
  private HighlightInfo processField(@NotNull final Project project,
                                     @NotNull final PsiField field,
                                     @NotNull PsiIdentifier identifier,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull GlobalUsageHelper helper) {
    if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
      return null;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(field) && !isImplicitUsage(myProject, field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.used", identifier.getText());

        HighlightInfo highlightInfo = suggestionsToMakeFieldUsed(field, identifier, message);
        if (!field.hasInitializer()) {
          QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), QuickFixFactory.getInstance().createCreateConstructorParameterFromFieldFix(field));
        }
        return highlightInfo;
      }

      final boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced && !isImplicitRead(project, field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.used.for.reading", identifier.getText());
        return suggestionsToMakeFieldUsed(field, identifier, message);
      }

      if (field.hasInitializer()) {
        return null;
      }
      final boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
      if (!writeReferenced && !isImplicitWrite(project, field, progress)) {
        String message = JavaErrorMessages.message("private.field.is.not.assigned", identifier.getText());
        final HighlightInfo info = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);

        QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field), myUnusedSymbolKey);
        QuickFixAction.registerQuickFixAction(info, HighlightMethodUtil.getFixRange(field), QuickFixFactory.getInstance().createCreateConstructorParameterFromFieldFix(
          field));
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, new Processor<String>() {
          @Override
          public boolean process(final String annoName) {
            QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance()
              .createAddToDependencyInjectionAnnotationsFix(project, annoName, "fields"));
            return true;
          }
        });
        return info;
      }
    }
    else if (isImplicitUsage(myProject, field, progress)) {
      return null;
    }
    else if (isFieldUnused(myProject, myFile, field, progress, helper)) {
      return formatUnusedSymbolHighlightInfo(project, "field.is.not.used", field, "fields", myDeadCodeKey, myDeadCodeInfoType, identifier);
    }
    return null;
  }

  public static boolean isFieldUnused(@NotNull Project project,
                                      @NotNull PsiFile containingFile,
                                      @NotNull PsiField field,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull GlobalUsageHelper helper) {
    if (helper.isLocallyUsed(field) || !weAreSureThereAreNoUsages(project, containingFile, field, progress, helper)) {
      return false;
    }
    return !(field instanceof PsiEnumConstant) || !isEnumValuesMethodUsed(project, containingFile, field, progress, helper);
  }

  private HighlightInfo suggestionsToMakeFieldUsed(@NotNull PsiField field, @NotNull PsiIdentifier identifier, @NotNull String message) {
    HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createRemoveUnusedVariableFix(field), myUnusedSymbolKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, false, field), myUnusedSymbolKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(false, true, field), myUnusedSymbolKey);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateGetterOrSetterFix(true, true, field), myUnusedSymbolKey);
    return highlightInfo;
  }

  private static boolean isOverriddenOrOverrides(PsiMethod method) {
    boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
  }

  @Nullable
  private HighlightInfo processParameter(@NotNull Project project,
                                         @NotNull PsiParameter parameter,
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
        if (isInjected(project, method)) return null;
        HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
        if (highlightInfo != null) {
          QuickFixFactory.getInstance().registerFixesForUnusedParameter(parameter, highlightInfo);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement && !PsiUtil.isIgnoredName(parameter.getName())) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter, identifier, progress);
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createRenameToIgnoredFix(parameter), myUnusedSymbolKey);
        return highlightInfo;
      }
    }

    return null;
  }

  @Nullable
  private HighlightInfo checkUnusedParameter(@NotNull PsiParameter parameter,
                                             @NotNull PsiIdentifier identifier,
                                             @NotNull ProgressIndicator progress) {
    if (!myRefCountHolder.isReferenced(parameter) && !isImplicitUsage(myProject, parameter, progress)) {
      String message = JavaErrorMessages.message("parameter.is.not.used", identifier.getText());
      return createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
    }
    return null;
  }

  @Nullable
  private HighlightInfo processMethod(@NotNull final Project project,
                                      @NotNull final PsiMethod method,
                                      @NotNull PsiIdentifier identifier,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull GlobalUsageHelper helper) {
    if (isMethodReferenced(myProject, myFile, method, progress, helper)) return null;
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
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(method), highlightDisplayKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(method, new Processor<String>() {
      @Override
      public boolean process(final String annoName) {
        IntentionAction fix = QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName, "methods");
        QuickFixAction.registerQuickFixAction(highlightInfo, fix);
        return true;
      }
    });
    return highlightInfo;
  }

  public static boolean isMethodReferenced(@NotNull Project project,
                                           @NotNull PsiFile containingFile,
                                           @NotNull PsiMethod method,
                                           @NotNull ProgressIndicator progress,
                                           @NotNull GlobalUsageHelper helper) {
    if (helper.isLocallyUsed(method)) return true;

    boolean aPrivate = method.hasModifierProperty(PsiModifier.PRIVATE);
    PsiClass containingClass = method.getContainingClass();
    if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return true;
    if (aPrivate) {
      if (isIntentionalPrivateConstructor(method, containingClass)) {
        return true;
      }
      if (isImplicitUsage(project, method, progress)) {
        return true;
      }
      if (!helper.isCurrentFileAlreadyChecked()) {
        return !weAreSureThereAreNoUsages(project, containingFile, method, progress, helper);
      }
    }
    else {
      //class maybe used in some weird way, e.g. from XML, therefore the only constructor is used too
      if (containingClass != null && method.isConstructor()
          && containingClass.getConstructors().length == 1
          && isClassUsed(project, containingFile, containingClass, progress, helper)) {
        return true;
      }
      if (isImplicitUsage(project, method, progress)) return true;

      if (method.findSuperMethods().length != 0) {
        return true;
      }
      if (!weAreSureThereAreNoUsages(project, containingFile, method, progress, helper)) {
        return true;
      }
    }
    return false;
  }

  private static boolean weAreSureThereAreNoUsages(@NotNull Project project,
                                                   @NotNull PsiFile containingFile,
                                                   @NotNull PsiMember member,
                                                   @NotNull ProgressIndicator progress,
                                                   @NotNull GlobalUsageHelper helper) {
    if (!helper.shouldCheckUsages(member)) return false;

    String name = member.getName();
    if (name == null) return false;
    SearchScope useScope = member.getUseScope();
    PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(project);
    PsiFile ignoreFile = helper.isCurrentFileAlreadyChecked() ? containingFile : null;
    if (useScope instanceof GlobalSearchScope) {
      // some classes may have references from within XML outside dependent modules, e.g. our actions
      if (member instanceof PsiClass) {
        useScope = GlobalSearchScope.projectScope(project).uniteWith((GlobalSearchScope)useScope);
      }

      PsiSearchHelper.SearchCostResult cheapEnough = searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, ignoreFile, progress);
      if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return false;

      //search usages if it cheap
      //if count is 0 there is no usages since we've called myRefCountHolder.isReferenced() before
      if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES && !canBeReferencedViaWeirdNames(member, containingFile)) {
        return true;
      }

      if (member instanceof PsiMethod) {
        String propertyName = PropertyUtil.getPropertyName(member);
        if (propertyName != null) {
          SearchScope fileScope = containingFile.getUseScope();
          if (fileScope instanceof GlobalSearchScope &&
              searchHelper.isCheapEnoughToSearch(propertyName, (GlobalSearchScope)fileScope, ignoreFile, progress) ==
              PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
            return false;
          }
        }
      }
    }
    if (ReferencesSearch.search(member, useScope, true).findFirst() != null) return false;
    return !(useScope instanceof GlobalSearchScope) || !foundUsageInText(member, (GlobalSearchScope)useScope, searchHelper, ignoreFile);
  }

  private static boolean foundUsageInText(@NotNull PsiMember member,
                                          @NotNull GlobalSearchScope scope,
                                          @NotNull PsiSearchHelper searchHelper,
                                          final PsiFile ignoreFile) {
    return !searchHelper.processUsagesInNonJavaFiles(member, member.getName(), new PsiNonJavaFileReferenceProcessor() {
      @Override
      public boolean process(final PsiFile psiFile, final int startOffset, final int endOffset) {
        if (psiFile == ignoreFile) return true; // ignore usages in containingFile because isLocallyUsed() method would have caught that
        PsiElement element = psiFile.findElementAt(startOffset);
        return element instanceof PsiComment; // ignore comments
      }
    }, scope);
  }

  private static boolean isEnumValuesMethodUsed(@NotNull Project project,
                                                @NotNull PsiFile containingFile,
                                                @NotNull PsiMember member,
                                                @NotNull ProgressIndicator progress,
                                                @NotNull GlobalUsageHelper helper) {
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass == null || !(containingClass instanceof PsiClassImpl)) return true;
    final PsiMethod valuesMethod = ((PsiClassImpl)containingClass).getValuesMethod();
    return valuesMethod == null || isMethodReferenced(project, containingFile, valuesMethod, progress, helper);
  }

  private static boolean canBeReferencedViaWeirdNames(@NotNull PsiMember member, @NotNull PsiFile containingFile) {
    if (member instanceof PsiClass) return false;
    if (!(containingFile instanceof PsiJavaFile)) return true;  // Groovy field can be referenced from Java by getter
    if (member instanceof PsiField) return false;  //Java field cannot be referenced by anything but its name
    if (member instanceof PsiMethod) {
      return PropertyUtil.isSimplePropertyAccessor((PsiMethod)member);  //Java accessors can be referenced by field name from Groovy
    }
    return false;
  }

  @Nullable
  private HighlightInfo processClass(@NotNull Project project,
                                     @NotNull PsiClass aClass,
                                     @NotNull PsiIdentifier identifier,
                                     @NotNull ProgressIndicator progress,
                                     @NotNull GlobalUsageHelper helper) {
    if (isClassUsed(project, myFile, aClass, progress, helper)) return null;

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
    return formatUnusedSymbolHighlightInfo(myProject, pattern, aClass, "classes", highlightDisplayKey, highlightInfoType, identifier);
  }

  public static boolean isClassUsed(@NotNull Project project,
                                    @NotNull PsiFile containingFile,
                                    @NotNull PsiClass aClass,
                                    @NotNull ProgressIndicator progress,
                                    @NotNull GlobalUsageHelper helper) {
    Boolean result = helper.unusedClassCache.get(aClass);
    if (result == null) {
      result = isReallyUsed(project, containingFile, aClass, progress, helper);
      helper.unusedClassCache.put(aClass, result);
    }
    return result;
  }

  private static boolean isReallyUsed(@NotNull Project project,
                                      @NotNull PsiFile containingFile,
                                      @NotNull PsiClass aClass,
                                      @NotNull ProgressIndicator progress,
                                      @NotNull GlobalUsageHelper helper) {
    if (isImplicitUsage(project, aClass, progress) || helper.isLocallyUsed(aClass)) return true;
    if (helper.isCurrentFileAlreadyChecked()) {
      if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE) ||
             aClass.getParent() instanceof PsiDeclarationStatement ||
             aClass instanceof PsiTypeParameter) return false;
    }
    return !weAreSureThereAreNoUsages(project, containingFile, aClass, progress, helper);
  }

  private static HighlightInfo formatUnusedSymbolHighlightInfo(@NotNull final Project project,
                                                               @NotNull @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String pattern,
                                                               @NotNull final PsiNameIdentifierOwner aClass,
                                                               @NotNull final String element,
                                                               HighlightDisplayKey highlightDisplayKey,
                                                               @NotNull HighlightInfoType highlightInfoType,
                                                               @NotNull PsiElement identifier) {
    String symbolName = aClass.getName();
    String message = JavaErrorMessages.message(pattern, symbolName);
    final HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, highlightInfoType);
    QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createSafeDeleteFix(aClass), highlightDisplayKey);
    SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes((PsiModifierListOwner)aClass, new Processor<String>() {
      @Override
      public boolean process(final String annoName) {
        QuickFixAction
          .registerQuickFixAction(highlightInfo,
                                  QuickFixFactory.getInstance().createAddToDependencyInjectionAnnotationsFix(project, annoName, element));
        return true;
      }
    });
    return highlightInfo;
  }

  @Nullable
  private HighlightInfo processImport(@NotNull PsiImportStatementBase importStatement, @NotNull HighlightDisplayKey unusedImportKey) {
    // jsp include directive hack
    if (importStatement.isForeignFileImport()) return null;

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

    int entryIndex = JavaCodeStyleManager.getInstance(myProject).findEntryIndex(importStatement);
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

    QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createOptimizeImportsFix(), unusedImportKey);
    QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createEnableOptimizeImportsOnTheFlyFix(), unusedImportKey);
    myHasRedundantImports = true;
    return info;
  }


  private static boolean isIntentionalPrivateConstructor(@NotNull PsiMethod method, PsiClass containingClass) {
    return method.isConstructor() &&
           method.getParameterList().getParametersCount() == 0 &&
           containingClass != null &&
           containingClass.getConstructors().length == 1;
  }
}
