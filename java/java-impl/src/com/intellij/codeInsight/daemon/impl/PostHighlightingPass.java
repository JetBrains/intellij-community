/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PostHighlightingPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PostHighlightingPass");
  private RefCountHolder myRefCountHolder;
  private final PsiFile myFile;
  @Nullable private final Editor myEditor;
  private final int myStartOffset;
  private final int myEndOffset;

  private Collection<HighlightInfo> myHighlights;
  private boolean myHasRedundantImports;
  private final JavaCodeStyleManager myStyleManager;
  private int myCurentEntryIndex;
  private boolean myHasMissortedImports;
  private final ImplicitUsageProvider[] myImplicitUsageProviders;
  private UnusedDeclarationInspection myDeadCodeInspection;
  private UnusedSymbolLocalInspection myUnusedSymbolInspection;
  private HighlightDisplayKey myUnusedSymbolKey;
  private boolean myDeadCodeEnabled;
  private boolean myInLibrary;
  private HighlightDisplayKey myDeadCodeKey;
  private HighlightInfoType myDeadCodeInfoType;

  private PostHighlightingPass(@NotNull Project project,
                               @NotNull PsiFile file,
                               @Nullable Editor editor,
                               @NotNull Document document,
                               int startOffset,
                               int endOffset) {
    super(project, document, true);
    myFile = file;
    myEditor = editor;
    myStartOffset = startOffset;
    myEndOffset = endOffset;

    myStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myCurentEntryIndex = -1;

    myImplicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
  }

  PostHighlightingPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor, int startOffset, int endOffset) {
    this(project, file, editor, editor.getDocument(), startOffset, endOffset);
  }

  public PostHighlightingPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Document document, int startOffset, int endOffset) {
    this(project, file, null, document, startOffset, endOffset);
  }

  public void doCollectInformation(final ProgressIndicator progress) {
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    final FileStatusMap fileStatusMap = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap();
    final List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    final Set<PsiElement> elementSet = new THashSet<PsiElement>();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightLevelUtil.shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      elementSet.addAll(elements);
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    myInLibrary = fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile);

    myRefCountHolder = RefCountHolder.getInstance(myFile);
    if (!myRefCountHolder.retrieveUnusedReferencesInfo(new Runnable() {
      public void run() {
        collectHighlights(elementSet, highlights, progress);
        myHighlights = highlights;
        for (HighlightInfo info : highlights) {
          if (info.getSeverity() == HighlightSeverity.ERROR) {
            fileStatusMap.setErrorFoundFlag(myDocument, true);
            break;
          }
        }
      }
    })) {
      // we must be sure GHP will restart
      fileStatusMap.markFileScopeDirty(getDocument(), Pass.UPDATE_ALL);
      GeneralHighlightingPass.cancelAndRestartDaemonLater(progress, myProject, this);
    }
  }

  public void doApplyInformationToEditor() {
    if (myHighlights == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myHighlights, Pass.POST_UPDATE_ALL);

    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, myFile, getId());

    if (timeToOptimizeImports() && myEditor != null) {
      optimizeImportsOnTheFly();
    }
  }

  private void optimizeImportsOnTheFly() {
    if (myHasRedundantImports || myHasMissortedImports) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  OptimizeImportsFix optimizeImportsFix = new OptimizeImportsFix();
                  if (optimizeImportsFix.isAvailable(myProject, myEditor, myFile) && myFile.isWritable()) {
                    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                    optimizeImportsFix.invoke(myProject, myEditor, myFile);
                  }
                }
              });
            }
          });
        }
      });
    }
  }

  @TestOnly
  public Collection<HighlightInfo> getHighlights() {
    return myHighlights;
  }

  private void collectHighlights(@NotNull Collection<PsiElement> elements, @NotNull final List<HighlightInfo> result, @NotNull ProgressIndicator progress) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile();
    myUnusedSymbolKey = HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME);
    boolean unusedSymbolEnabled = profile.isToolEnabled(myUnusedSymbolKey, myFile);
    HighlightDisplayKey unusedImportKey = HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME);
    boolean unusedImportEnabled = profile.isToolEnabled(unusedImportKey, myFile);
    LocalInspectionToolWrapper unusedSymbolTool = (LocalInspectionToolWrapper)profile.getInspectionTool(UnusedSymbolLocalInspection.SHORT_NAME,
                                                                                                        myFile);
    myUnusedSymbolInspection = unusedSymbolTool == null ? null : (UnusedSymbolLocalInspection)unusedSymbolTool.getTool();
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || myUnusedSymbolInspection != null);

    myDeadCodeKey = HighlightDisplayKey.find(UnusedDeclarationInspection.SHORT_NAME);
    myDeadCodeInspection = (UnusedDeclarationInspection)profile.getInspectionTool(UnusedDeclarationInspection.SHORT_NAME, myFile);
    myDeadCodeEnabled = profile.isToolEnabled(myDeadCodeKey, myFile);
    if (unusedImportEnabled && JspPsiUtil.isInJspFile(myFile)) {
      final JspFile jspFile = JspPsiUtil.getJspFile(myFile);
      if (jspFile != null) {
        unusedImportEnabled = !JspSpiUtil.isIncludedOrIncludesSomething(jspFile);
      }
    }

    myDeadCodeInfoType = myDeadCodeKey == null ? null : new HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(myDeadCodeKey, myFile).getSeverity(), HighlightInfoType.UNUSED_SYMBOL.getAttributesKey());

    if (!unusedSymbolEnabled && !unusedImportEnabled) {
      return;
    }
    for (PsiElement element : elements) {
      progress.checkCanceled();

      if (unusedSymbolEnabled && element instanceof PsiIdentifier) {
        PsiIdentifier identifier = (PsiIdentifier)element;
        HighlightInfo info = processIdentifier(identifier, progress);
        if (info != null) {
          result.add(info);
        }
      }
      else if (unusedImportEnabled && element instanceof PsiImportList) {
        final PsiImportStatementBase[] imports = ((PsiImportList)element).getAllImportStatements();
        for (PsiImportStatementBase statement : imports) {
          ProgressManager.checkCanceled();
          final HighlightInfo info = processImport(statement, unusedImportKey);
          if (info != null) {
            result.add(info);
          }
        }
      }
    }
  }

  @Nullable
  private HighlightInfo processIdentifier(PsiIdentifier identifier, ProgressIndicator progress) {
    if (InspectionManagerEx.inspectionResultSuppressed(identifier, myUnusedSymbolInspection)) return null;
    PsiElement parent = identifier.getParent();
    if (PsiUtilBase.hasErrorElementChild(parent)) return null;
    HighlightInfo info;

    if (parent instanceof PsiLocalVariable && myUnusedSymbolInspection.LOCAL_VARIABLE) {
      info = processLocalVariable((PsiLocalVariable)parent, progress);
    }
    else if (parent instanceof PsiField && myUnusedSymbolInspection.FIELD) {
      final PsiField psiField = (PsiField)parent;
      info = processField(psiField, identifier, progress);
    }
    else if (parent instanceof PsiParameter && myUnusedSymbolInspection.PARAMETER) {
      info = processParameter((PsiParameter)parent, progress);
    }
    else if (parent instanceof PsiMethod && myUnusedSymbolInspection.METHOD) {
      info = processMethod((PsiMethod)parent, progress);
    }
    else if (parent instanceof PsiClass && identifier.equals(((PsiClass)parent).getNameIdentifier()) && myUnusedSymbolInspection.CLASS) {
      info = processClass((PsiClass)parent, progress);
    }
    else {
      return null;
    }
    return info;
  }


  @Nullable
  private HighlightInfo processLocalVariable(PsiLocalVariable variable, ProgressIndicator progress) {
    PsiIdentifier identifier = variable.getNameIdentifier();
    if (identifier == null) return null;
    if (isImplicitUsage(variable, progress)) return null;
    if (!myRefCountHolder.isReferenced(variable)) {
      String message = JavaErrorMessages.message("local.variable.is.never.used", identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), myUnusedSymbolKey);
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


  private boolean isImplicitUsage(final PsiModifierListOwner element, ProgressIndicator progress) {
    if (UnusedSymbolLocalInspection.isInjected(element, myUnusedSymbolInspection)) return true;
    for (ImplicitUsageProvider provider : myImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitUsage(element)) {
        return true;
      }
    }

    return false;
  }

  private boolean isImplicitRead(final PsiVariable element, ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: myImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitRead(element)) {
        return true;
      }
    }
    return false;
  }

  private boolean isImplicitWrite(final PsiVariable element, ProgressIndicator progress) {
    for(ImplicitUsageProvider provider: myImplicitUsageProviders) {
      progress.checkCanceled();
      if (provider.isImplicitWrite(element)) {
        return true;
      }
    }
    return false;
  }

  private static HighlightInfo createUnusedSymbolInfo(PsiElement element, String message, final HighlightInfoType highlightInfoType) {
    return HighlightInfo.createHighlightInfo(highlightInfoType, element, message);
  }

  @Nullable
  private HighlightInfo processField(final PsiField field, final PsiIdentifier identifier, ProgressIndicator progress) {
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(field) && !isImplicitUsage(field, progress)) {
        if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
          return null;
        }
        String message = JavaErrorMessages.message("private.field.is.not.used", identifier.getText());

        HighlightInfo highlightInfo = suggestionsToMakeFieldUsed(field, identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), new CreateConstructorParameterFromFieldFix(field), null);
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
        QuickFixAction.registerQuickFixAction(info, HighlightMethodUtil.getFixRange(field), new CreateConstructorParameterFromFieldFix(field), null);
        SpecialAnnotationsUtil.createAddToSpecialAnnotationFixes(field, new Processor<String>() {
          public boolean process(final String annoName) {
            QuickFixAction.registerQuickFixAction(info, myUnusedSymbolInspection.createQuickFix(annoName, "fields"));
            return true;
          }
        });
        return info;
      }
    }
    else if (isImplicitUsage(field, progress)) {
      return null;
    }
    else if (!myRefCountHolder.isReferenced(field) && weAreSureThereAreNoUsages(field, progress)) {
      return formatUnusedSymbolHighlightInfo("field.is.not.used", field, "fields", myDeadCodeKey, myDeadCodeInfoType);
    }
    return null;
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
  private HighlightInfo processParameter(PsiParameter parameter, ProgressIndicator progress) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
      if (PsiUtilBase.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() ||
           method.hasModifierProperty(PsiModifier.PRIVATE) ||
           method.hasModifierProperty(PsiModifier.STATIC) ||
           !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
           myUnusedSymbolInspection.REPORT_PARAMETER_FOR_PUBLIC_METHODS &&
           !isOverriddenOrOverrides(method)) &&
          !method.hasModifierProperty(PsiModifier.NATIVE) &&
          !HighlightMethodUtil.isSerializationRelatedMethod(method, method.getContainingClass()) &&
          !PsiClassImplUtil.isMainMethod(method)) {
        HighlightInfo highlightInfo = checkUnusedParameter(parameter, progress);
        if (highlightInfo != null) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedParameterFix(parameter), myUnusedSymbolKey);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter, progress);
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(UnusedSymbolLocalInspection.DISPLAY_NAME), myUnusedSymbolKey);
        return highlightInfo;
      }
    }

    return null;
  }

  @Nullable
  private HighlightInfo checkUnusedParameter(final PsiParameter parameter, ProgressIndicator progress) {
    if (!myRefCountHolder.isReferenced(parameter) && !isImplicitUsage(parameter, progress)) {
      PsiIdentifier identifier = parameter.getNameIdentifier();
      assert identifier != null;
      String message = JavaErrorMessages.message("parameter.is.not.used", identifier.getText());
      return createUnusedSymbolInfo(identifier, message, HighlightInfoType.UNUSED_SYMBOL);
    }
    return null;
  }

  @Nullable
  private HighlightInfo processMethod(final PsiMethod method, ProgressIndicator progress) {
    if (myRefCountHolder.isReferenced(method)) return null;
    boolean isPrivate = method.hasModifierProperty(PsiModifier.PRIVATE);
    PsiClass containingClass = method.getContainingClass();
    HighlightInfoType highlightInfoType = HighlightInfoType.UNUSED_SYMBOL;
    HighlightDisplayKey highlightDisplayKey = myUnusedSymbolKey;

    if (isPrivate) {
      if (HighlightMethodUtil.isSerializationRelatedMethod(method, containingClass) ||
          isIntentionalPrivateConstructor(method, containingClass)) {
        return null;
      }
      if (isImplicitUsage(method, progress)) {
        return null;
      }
    }
    else {
      //class maybe used in some weird way, e.g. from XML, therefore the only constructor is used too
      if (containingClass != null && method.isConstructor() && containingClass.getConstructors().length == 1 && isClassUnused(containingClass,
                                                                                                                              progress) == USED) return null;
      if (isImplicitUsage(method, progress)) return null;

      if (method.findSuperMethods().length != 0) {
        return null;
      }
      if (!weAreSureThereAreNoUsages(method, progress)) {
        return null;
      }
      highlightInfoType = myDeadCodeInfoType;
      highlightDisplayKey = myDeadCodeKey;
    }
    String key = isPrivate
                 ? method.isConstructor() ? "private.constructor.is.not.used" : "private.method.is.not.used"
                 : method.isConstructor() ? "constructor.is.not.used" : "method.is.not.used";
    String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
    String message = JavaErrorMessages.message(key, symbolName);
    PsiIdentifier identifier = method.getNameIdentifier();
    final HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, highlightInfoType);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(method), highlightDisplayKey);
    SpecialAnnotationsUtil.createAddToSpecialAnnotationFixes(method, new Processor<String>() {
      public boolean process(final String annoName) {
        QuickFixAction.registerQuickFixAction(highlightInfo, myUnusedSymbolInspection.createQuickFix(annoName, "methods"));
        return true;
      }
    });
    return highlightInfo;
  }

  private boolean weAreSureThereAreNoUsages(PsiMember member, ProgressIndicator progress) {
    if (myInLibrary) return false;
    if (!myDeadCodeEnabled) return false;
    if (myDeadCodeInspection.isEntryPoint(member)) return false;

    String name = member.getName();
    if (name == null) return false;
    SearchScope useScope = member.getUseScope();
    if (!(useScope instanceof GlobalSearchScope)) return false;
    GlobalSearchScope scope = (GlobalSearchScope)useScope;
    // some classes may have references from within XML outside dependent modules, e.g. our actions
    if (member instanceof PsiClass) scope = scope.uniteWith(GlobalSearchScope.projectScope(myProject));

    PsiSearchHelper.SearchCostResult cheapEnough = myFile.getManager().getSearchHelper().isCheapEnoughToSearch(name, scope, myFile,
                                                                                                               progress);
    if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return false;

    //search usages if it cheap
    //if count is 0 there is no usages since we've called myRefCountHolder.isReferenced() before
    if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES && !canbeReferencedViaWeirdNames(member)) return true;

    Query<PsiReference> query = member instanceof PsiMethod
                                ? MethodReferencesSearch.search((PsiMethod)member, scope, true)
                                : ReferencesSearch.search(member, scope, true);
    return query.findFirst() == null;
  }

  private static boolean canbeReferencedViaWeirdNames(PsiMember member) {
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
  private HighlightInfo processClass(PsiClass aClass, ProgressIndicator progress) {
    int usage = isClassUnused(aClass, progress);
    if (usage == USED) return null;

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
    return formatUnusedSymbolHighlightInfo(pattern, aClass, "classes", highlightDisplayKey, highlightInfoType);
  }

  private static final int USED = 1;
  private static final int UNUSED_LOCALLY = 2;
  private static final int UNUSED_GLOBALLY = 3;
  private final TObjectIntHashMap<PsiClass> unusedClassCache = new TObjectIntHashMap<PsiClass>();
  private int isClassUnused(PsiClass aClass, ProgressIndicator progress) {
    if (aClass == null) return USED;
    int result = unusedClassCache.get(aClass);
    if (result == 0) {
      result = isReallyUnused(aClass, progress);
      unusedClassCache.put(aClass, result);
    }
    return result;
  }

  private int isReallyUnused(PsiClass aClass, ProgressIndicator progress) {
    if (isImplicitUsage(aClass, progress) || myRefCountHolder.isReferenced(aClass)) return USED;
    if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE) ||
           aClass.getParent() instanceof PsiDeclarationStatement ||
           aClass instanceof PsiTypeParameter) return UNUSED_LOCALLY;
    if (weAreSureThereAreNoUsages(aClass, progress)) return UNUSED_GLOBALLY;
    return USED;
  }

  private HighlightInfo formatUnusedSymbolHighlightInfo(@PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String pattern,
                                                        PsiNameIdentifierOwner aClass,
                                                        final String element,
                                                        final HighlightDisplayKey highlightDisplayKey,
                                                        final HighlightInfoType highlightInfoType) {
    String symbolName = aClass.getName();
    String message = JavaErrorMessages.message(pattern, symbolName);
    PsiElement identifier = aClass.getNameIdentifier();
    final HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message, highlightInfoType);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(aClass), highlightDisplayKey);
    SpecialAnnotationsUtil.createAddToSpecialAnnotationFixes((PsiModifierListOwner)aClass, new Processor<String>() {
      public boolean process(final String annoName) {
        QuickFixAction.registerQuickFixAction(highlightInfo, myUnusedSymbolInspection.createQuickFix(annoName, element));
        return true;
      }
    });
    return highlightInfo;
  }

  @Nullable
  private HighlightInfo processImport(PsiImportStatementBase importStatement, HighlightDisplayKey unusedImportKey) {
    // jsp include directive hack
    if (importStatement instanceof JspxImportStatement && ((JspxImportStatement)importStatement).isForeignFileImport()) return null;

    if (PsiUtilBase.hasErrorElementChild(importStatement)) return null;

    boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
    if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
      //check import from same package
      String packageName = ((PsiClassOwner)importStatement.getContainingFile()).getPackageName();
      PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
      PsiElement resolved = reference == null ? null : reference.resolve();
      if (resolved instanceof PsiPackage) {
        isRedundant = packageName.equals(((PsiPackage)resolved).getQualifiedName());
      }
      else if (resolved instanceof PsiClass && !importStatement.isOnDemand()) {
        String qName = ((PsiClass)resolved).getQualifiedName();
        if (qName != null) {
          String name = ((PsiClass)resolved).getName();
          isRedundant = qName.equals(packageName + '.' + name);
        }
      }
    }

    if (isRedundant) {
      return registerRedundantImport(importStatement, unusedImportKey);
    }

    int entryIndex = myStyleManager.findEntryIndex(importStatement);
    if (entryIndex < myCurentEntryIndex) {
      myHasMissortedImports = true;
    }
    myCurentEntryIndex = entryIndex;

    return null;
  }

  private HighlightInfo registerRedundantImport(PsiImportStatementBase importStatement, HighlightDisplayKey unusedImportKey) {
    HighlightInfo info = HighlightInfo.createHighlightInfo(JavaHightlightInfoTypes.UNUSED_IMPORT, importStatement, InspectionsBundle.message("unused.import.statement"));

    QuickFixAction.registerQuickFixAction(info, new OptimizeImportsFix(), unusedImportKey);
    QuickFixAction.registerQuickFixAction(info, new EnableOptimizeImportsOnTheFlyFix(), unusedImportKey);
    myHasRedundantImports = true;
    return info;
  }

  private boolean timeToOptimizeImports() {
    if (!CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) return false;

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    // dont optimize out imports in JSP since it can be included in other JSP
    if (file == null || !codeAnalyzer.isHighlightingAvailable(file) || !(file instanceof PsiJavaFile) || file instanceof JspFile) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false;
    boolean errors = containsErrorsPreventingOptimize(file);

    return !errors && codeAnalyzer.canChangeFileSilently(myFile);
  }

  private boolean containsErrorsPreventingOptimize(PsiFile file) {
    List<HighlightInfo> errors = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.ERROR, myProject);

    // ignore unresolved imports errors
    PsiImportList importList = ((PsiJavaFile)file).getImportList();
    if (importList != null) {
      TextRange importsRange = importList.getTextRange();
      for (HighlightInfo error : errors) {
        if (!error.type.equals(HighlightInfoType.WRONG_REF)) return true;
        if (!importsRange.contains(error.getActualStartOffset()) || !importsRange.contains(error.getActualEndOffset())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isIntentionalPrivateConstructor(PsiMethod method, PsiClass containingClass) {
    return method.isConstructor() &&
           method.getParameterList().getParametersCount() == 0 &&
           containingClass != null &&
           containingClass.getConstructors().length == 1;
  }
}
