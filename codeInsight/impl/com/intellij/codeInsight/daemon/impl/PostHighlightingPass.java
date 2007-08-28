package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.lang.LangBundle;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
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
  private final CodeStyleManagerEx myStyleManager;
  private int myCurentEntryIndex;
  private boolean myHasMissortedImports;
  private ImplicitUsageProvider[] myImplicitUsageProviders;

  private PostHighlightingPass(@NotNull Project project,
                               @NotNull PsiFile file,
                               @Nullable Editor editor,
                               @NotNull Document document,
                               int startOffset,
                               int endOffset) {
    super(project, document);
    myFile = file;
    myEditor = editor;
    myStartOffset = startOffset;
    myEndOffset = endOffset;

    myStyleManager = (CodeStyleManagerEx)CodeStyleManager.getInstance(myProject);
    myCurentEntryIndex = -1;

    myImplicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
  }

  public PostHighlightingPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor, int startOffset, int endOffset) {
    this(project, file, editor, editor.getDocument(), startOffset, endOffset);
  }

  public PostHighlightingPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Document document, int startOffset, int endOffset) {
    this(project, file, null, document, startOffset, endOffset);
  }

  public void doCollectInformation(ProgressIndicator progress) {
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    myRefCountHolder = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().getRefCountHolder(myFile, progress);
    myRefCountHolder.assertIsTouched();
    List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getPrimaryLanguages();
    Set<PsiElement> elementSet = new THashSet<PsiElement>();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightUtil.shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      elementSet.addAll(elements);
    }
    collectHighlights(elementSet, highlights);

    boolean doubleCheckUsages = false;
    if (PsiUtil.isInJspFile(myFile)) {
      final PsiFile[] includingFiles = JspSpiUtil.getReferencingFiles(PsiUtil.getJspFile(myFile));
      doubleCheckUsages = includingFiles.length > 1 || includingFiles.length == 1 && includingFiles[0] != myFile;
    }

    List<PsiNamedElement> unusedDcls = myRefCountHolder.getUnusedDcls();
    for (PsiNamedElement unusedDcl : unusedDcls) {
      if (doubleCheckUsages) {
        if (ReferencesSearch.search(unusedDcl).findFirst() != null) continue;
      }

      String dclType = StringUtil.capitalize(UsageViewUtil.getType(unusedDcl));
      if (dclType.length() == 0) dclType = LangBundle.message("java.terms.symbol");
      String message = MessageFormat.format(JavaErrorMessages.message("symbol.is.never.used"), dclType, unusedDcl.getName());

      HighlightInfo highlightInfo = createUnusedSymbolInfo(unusedDcl.getNavigationElement(), message);
      highlights.add(highlightInfo);
    }

    myHighlights = highlights;
  }

  public void doApplyInformationToEditor() {
    if (myHighlights == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                   myHighlights, Pass.POST_UPDATE_ALL);

    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, Pass.POST_UPDATE_ALL);

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
                  if (optimizeImportsFix.isAvailable(myProject, myEditor, myFile)
                      && myFile.isWritable()) {
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

  // for tests only
  public Collection<HighlightInfo> getHighlights() {
    return myHighlights;
  }

  private void collectHighlights(Collection<PsiElement> elements, List<HighlightInfo> array) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(myFile);
    boolean unusedSymbolEnabled = profile.isToolEnabled(HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));
    boolean unusedImportEnabled = profile.isToolEnabled(HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME));
    LocalInspectionToolWrapper unusedSymbolTool = (LocalInspectionToolWrapper)profile.getInspectionTool(UnusedSymbolLocalInspection.SHORT_NAME);
    final UnusedSymbolLocalInspection unusedSymbolInspection = unusedSymbolTool == null ? null : (UnusedSymbolLocalInspection)unusedSymbolTool.getTool();

    if (unusedImportEnabled && PsiUtil.isInJspFile(myFile)) {
      final PsiFile[] includingFiles = JspSpiUtil.getReferencingFiles(PsiUtil.getJspFile(myFile));
      unusedImportEnabled = includingFiles.length == 0 || includingFiles.length == 1 && includingFiles[0] == myFile;
    }

    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      if (element instanceof PsiIdentifier && unusedSymbolEnabled) {
        final HighlightInfo highlightInfo = processIdentifier((PsiIdentifier)element,unusedSymbolInspection);
        if (highlightInfo != null) array.add(highlightInfo);
      }
      else if (element instanceof PsiImportList && unusedImportEnabled) {
        final PsiImportStatementBase[] imports = ((PsiImportList)element).getAllImportStatements();
        for (PsiImportStatementBase statement : imports) {
          final HighlightInfo highlightInfo = processImport(statement);
          if (highlightInfo != null) array.add(highlightInfo);
        }
      }
      else if (element instanceof XmlAttributeValue) {
        final HighlightInfo highlightInfo = XmlHighlightVisitor.checkIdRefAttrValue((XmlAttributeValue)element, myRefCountHolder);
        if (highlightInfo != null) array.add(highlightInfo);
      }
    }
  }

  @Nullable
  private HighlightInfo processIdentifier(PsiIdentifier identifier, final UnusedSymbolLocalInspection unusedSymbolInspection) {
    if (InspectionManagerEx.inspectionResultSuppressed(identifier, unusedSymbolInspection)) return null;
    PsiElement parent = identifier.getParent();
    if (PsiUtil.hasErrorElementChild(parent)) return null;
    HighlightInfo info;

    if (parent instanceof PsiLocalVariable && unusedSymbolInspection.LOCAL_VARIABLE) {
      info = processLocalVariable((PsiLocalVariable)parent);
    }
    else if (parent instanceof PsiField && unusedSymbolInspection.FIELD) {
      final PsiField psiField = (PsiField)parent;
      info = processField(psiField, unusedSymbolInspection, identifier);
    }
    else if (parent instanceof PsiParameter && unusedSymbolInspection.PARAMETER) {
      info = processParameter((PsiParameter)parent, unusedSymbolInspection);
    }
    else if (parent instanceof PsiMethod && unusedSymbolInspection.METHOD) {
      info = processMethod((PsiMethod)parent, unusedSymbolInspection);
    }
    else if (parent instanceof PsiClass && identifier.equals(((PsiClass)parent).getNameIdentifier()) && unusedSymbolInspection.CLASS) {
      info = processClass((PsiClass)parent);
    }
    else {
      return null;
    }
    return info;
  }


  @Nullable
  private HighlightInfo processLocalVariable(PsiLocalVariable variable) {
    PsiIdentifier identifier = variable.getNameIdentifier();
    if (identifier == null) return null;

    if (!myRefCountHolder.isReferenced(variable) && !isImplicitUsage(variable)) {
      String message = MessageFormat.format(JavaErrorMessages.message("local.variable.is.never.used"), identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));
      return highlightInfo;
    }

    boolean referenced = myRefCountHolder.isReferencedForRead(variable);
    if (!referenced && !isImplicitRead(variable)) {
      String message = MessageFormat.format(JavaErrorMessages.message("local.variable.is.not.used.for.reading"), identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));
      return highlightInfo;
    }

    if (!variable.hasInitializer()) {
      referenced = myRefCountHolder.isReferencedForWrite(variable);
      if (!referenced && !isImplicitWrite(variable)) {
        String message = MessageFormat.format(JavaErrorMessages.message("local.variable.is.not.assigned"), identifier.getText());
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(UnusedSymbolLocalInspection.DISPLAY_NAME),
                                              HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));
        return unusedSymbolInfo;
      }
    }

    return null;
  }


  private boolean isImplicitUsage(final PsiElement element) {
    for(ImplicitUsageProvider provider: myImplicitUsageProviders) {
      if (provider.isImplicitUsage(element)) {
        return true;
      }
    }

    return false;
  }

  private boolean isImplicitRead(final PsiVariable element) {
    for(ImplicitUsageProvider provider: myImplicitUsageProviders) {
      if (provider.isImplicitRead(element)) {
        return true;
      }
    }
    return false;
  }

  private boolean isImplicitWrite(final PsiVariable element) {
    for(ImplicitUsageProvider provider: myImplicitUsageProviders) {
      if (provider.isImplicitWrite(element)) {
        return true;
      }
    }
    return false;
  }

  private static HighlightInfo createUnusedSymbolInfo(PsiElement element, String message) {
    return HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_SYMBOL, element, message);
  }

  @Nullable
  private HighlightInfo processField(final PsiField field, final UnusedSymbolLocalInspection unusedSymbolInspection,
                                     final PsiIdentifier identifier) {
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      final HighlightDisplayKey key = HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME);
      if (!myRefCountHolder.isReferenced(field) && !isImplicitUsage(field)) {
        if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
          return null;
        }
        String message = MessageFormat.format(JavaErrorMessages.message("private.field.is.not.used"), identifier.getText());

        HighlightInfo highlightInfo = suggestionsToMakeFieldUsed(field, key, identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateConstructorParameterFromFieldFix(field), key);
        return highlightInfo;
      }

      final boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced && !isImplicitRead(field)) {
        String message = MessageFormat.format(JavaErrorMessages.message("private.field.is.not.used.for.reading"), identifier.getText());
        return suggestionsToMakeFieldUsed(field, key, identifier, message);
      }

      if (!field.hasInitializer()) {
        final boolean isInjected = isInjected(field, unusedSymbolInspection);
        final boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
        if (!writeReferenced && !isInjected && !isImplicitWrite(field)) {
          String message = MessageFormat.format(JavaErrorMessages.message("private.field.is.not.assigned"), identifier.getText());
          final HighlightInfo info = createUnusedSymbolInfo(identifier, message);

          QuickFixAction.registerQuickFixAction(info, new CreateGetterOrSetterFix(false, true, field), key);
          QuickFixAction.registerQuickFixAction(info, new CreateConstructorParameterFromFieldFix(field), key);
          SpecialAnnotationsUtil.createAddToSpecialAnnotationFixes(field, new Processor<String>() {
            public boolean process(final String annoName) {
              QuickFixAction.registerQuickFixAction(info, unusedSymbolInspection.createQuickFix(annoName, field));
              return true;
            }
          });
          return info;
        }
      }
    }

    return null;
  }

  private static HighlightInfo suggestionsToMakeFieldUsed(final PsiField field, final HighlightDisplayKey key, final PsiIdentifier identifier,
                                                          final String message) {
    HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
    QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), key);
    QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterFix(true, false, field), key);
    QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterFix(false, true, field), key);
    QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterFix(true, true, field), key);
    return highlightInfo;
  }

  private static boolean isInjected(final PsiMember member, final UnusedSymbolLocalInspection unusedSymbolInspection) {
    return AnnotationUtil.isAnnotated(member, UnusedSymbolLocalInspection.STANDARD_INJECTION_ANNOS) ||
           AnnotationUtil.isAnnotated(member, unusedSymbolInspection.INJECTION_ANNOS);
  }

  private static boolean isOverriddenOrOverrides(PsiMethod method) {
    boolean overrides = SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    return overrides || OverridingMethodsSearch.search(method).findFirst() != null;
  }

  @Nullable
  private HighlightInfo processParameter(PsiParameter parameter, final UnusedSymbolLocalInspection unusedSymbolInspection) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
      if (PsiUtil.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.ABSTRACT) && unusedSymbolInspection.REPORT_PARAMETER_FOR_PUBLIC_METHODS && !isOverriddenOrOverrides(method))
          && !method.hasModifierProperty(PsiModifier.NATIVE)
          && !HighlightMethodUtil.isSerializationRelatedMethod(method)
          && !isMainMethod(method)
      ) {
        HighlightInfo highlightInfo = checkUnusedParameter(parameter);
        if (highlightInfo != null) {
          QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedParameterFix(parameter), HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      HighlightInfo highlightInfo = checkUnusedParameter(parameter);
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(UnusedSymbolLocalInspection.DISPLAY_NAME), HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));
        return highlightInfo;
      }
    }

    return null;
  }

  @Nullable
  private HighlightInfo checkUnusedParameter(final PsiParameter parameter) {
    if (!myRefCountHolder.isReferenced(parameter) && !isImplicitUsage(parameter)) {
      PsiIdentifier identifier = parameter.getNameIdentifier();
      assert identifier != null;
      String message = MessageFormat.format(JavaErrorMessages.message("parameter.is.not.used"), identifier.getText());
      return createUnusedSymbolInfo(identifier, message);
    }
    return null;
  }

  @Nullable
  private HighlightInfo processMethod(final PsiMethod method, final UnusedSymbolLocalInspection unusedSymbolInspection) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      final boolean isSetter = PropertyUtil.isSimplePropertySetter(method);
      final boolean isInjected = isSetter && isInjected(method, unusedSymbolInspection);
      if (!myRefCountHolder.isReferenced(method)) {
        if (isInjected || HighlightMethodUtil.isSerializationRelatedMethod(method) ||
            isIntentionalPrivateConstructor(method) ||
            isImplicitUsage(method)
        ) {
          return null;
        }
        String pattern = method.isConstructor() ? JavaErrorMessages.message("private.constructor.is.not.used") : JavaErrorMessages.message("private.method.is.not.used");
        String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
        String message = MessageFormat.format(pattern, symbolName);
        PsiIdentifier identifier = method.getNameIdentifier();
        final HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(method), HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));
        if (isSetter) {
          SpecialAnnotationsUtil.createAddToSpecialAnnotationFixes(method, new Processor<String>() {
            public boolean process(final String annoName) {
              QuickFixAction.registerQuickFixAction(highlightInfo, unusedSymbolInspection.createQuickFix(annoName, method));
              return true;
            }
          });
        }
        return highlightInfo;
      }
    }
    return null;
  }

  @Nullable
  private HighlightInfo processClass(PsiClass aClass) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME);
    if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(aClass) && !isImplicitUsage(aClass)) {
        String pattern = aClass.isInterface()
                         ? JavaErrorMessages.message("private.inner.interface.is.not.used") : JavaErrorMessages.message("private.inner.class.is.not.used");
        return formatUnusedSymbolHighlightInfo(aClass, pattern, key);
      }
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
      if (!myRefCountHolder.isReferenced(aClass) && !isImplicitUsage(aClass)) {
        return formatUnusedSymbolHighlightInfo(aClass, JavaErrorMessages.message("local.class.is.not.used"), key);
      }
    }
    else if (aClass instanceof PsiTypeParameter) {
      if (!myRefCountHolder.isReferenced(aClass) && !isImplicitUsage(aClass)) {
        return formatUnusedSymbolHighlightInfo(aClass, JavaErrorMessages.message("type.parameter.is.not.used"), key);
      }
    }
    return null;
  }

  private static HighlightInfo formatUnusedSymbolHighlightInfo(PsiClass aClass, String pattern, final HighlightDisplayKey key) {
    String symbolName = aClass.getName();
    String message = MessageFormat.format(pattern, symbolName);
    PsiIdentifier identifier = aClass.getNameIdentifier();
    HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(aClass), key);
    return highlightInfo;
  }

  @Nullable
  private HighlightInfo processImport(PsiImportStatementBase importStatement) {
    // jsp include directive hack
    if (importStatement instanceof JspxImportStatement && ((JspxImportStatement)importStatement).isForeignFileImport()) return null;

    if (PsiUtil.hasErrorElementChild(importStatement)) return null;

    boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
    if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
      //check import from same package
      String packageName = ((PsiJavaFile)importStatement.getContainingFile()).getPackageName();
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
      return registerRedundantImport(importStatement);
    }

    int entryIndex = myStyleManager.findEntryIndex(importStatement);
    if (entryIndex < myCurentEntryIndex) {
      myHasMissortedImports = true;
    }
    myCurentEntryIndex = entryIndex;

    return null;
  }

  private HighlightInfo registerRedundantImport(PsiImportStatementBase importStatement) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME);
    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_IMPORT, importStatement, InspectionsBundle.message("unused.import.statement"));

    QuickFixAction.registerQuickFixAction(info, new OptimizeImportsFix(), key);
    QuickFixAction.registerQuickFixAction(info, new EnableOptimizeImportsOnTheFlyFix(), key);
    myHasRedundantImports = true;
    return info;
  }

  private boolean timeToOptimizeImports() {
    if (!CodeStyleSettingsManager.getSettings(myProject).OPTIMIZE_IMPORTS_ON_THE_FLY) return false;

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    // dont optimize out imports in JSP since it can be included in other JSP
    if (file == null || !codeAnalyzer.isHighlightingAvailable(file) || !(file instanceof PsiJavaFile) || file instanceof JspFile) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false;
    HighlightInfo[] errors = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.ERROR, myProject);

    return errors.length == 0 && codeAnalyzer.canChangeFileSilently(myFile);
  }

  private static boolean isMainMethod(PsiMethod method) {
    if (!PsiType.VOID.equals(method.getReturnType())) return false;
    PsiElementFactory factory = method.getManager().getElementFactory();
    try {
      PsiMethod appMain = factory.createMethodFromText("void main(String[] args);", null);
      if (MethodSignatureUtil.areSignaturesEqual(method, appMain)) return true;
      PsiMethod appPremain = factory.createMethodFromText("void premain(String args, java.lang.instrument.Instrumentation i);", null);
      if (MethodSignatureUtil.areSignaturesEqual(method, appPremain)) return true;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return false;
  }

  private static boolean isIntentionalPrivateConstructor(PsiMethod method) {
    if (!method.isConstructor()) return false;
    if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (method.getParameterList().getParametersCount() > 0) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass != null && aClass.getConstructors().length == 1;
  }
}
