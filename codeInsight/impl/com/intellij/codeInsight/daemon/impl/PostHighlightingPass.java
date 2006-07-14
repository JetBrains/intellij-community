package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightSettings;
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

public class PostHighlightingPass extends TextEditorHighlightingPass {
  private static final String SYMBOL_IS_NOT_USED = JavaErrorMessages.message("symbol.is.never.used");
  private static final String LOCAL_VARIABLE_IS_NOT_USED = JavaErrorMessages.message("local.variable.is.never.used");
  private static final String LOCAL_VARIABLE_IS_NOT_USED_FOR_READING = JavaErrorMessages.message("local.variable.is.not.used.for.reading");
  private static final String LOCAL_VARIABLE_IS_NOT_ASSIGNED = JavaErrorMessages.message("local.variable.is.not.assigned");
  private static final String PRIVATE_FIELD_IS_NOT_USED_FOR_READING = JavaErrorMessages.message("private.field.is.not.used.for.reading");
  private static final String PARAMETER_IS_NOT_USED = JavaErrorMessages.message("parameter.is.not.used");
  private static final String PRIVATE_METHOD_IS_NOT_USED = JavaErrorMessages.message("private.method.is.not.used");
  private static final String PRIVATE_CONSTRUCTOR_IS_NOT_USED = JavaErrorMessages.message("private.constructor.is.not.used");
  private static final String PRIVATE_INNER_CLASS_IS_NOT_USED = JavaErrorMessages.message("private.inner.class.is.not.used");
  private static final String PRIVATE_INNER_INTERFACE_IS_NOT_USED = JavaErrorMessages.message("private.inner.interface.is.not.used");
  private static final String TYPE_PARAMETER_IS_NOT_USED = JavaErrorMessages.message("type.parameter.is.not.used");
  private static final String LOCAL_CLASS_IS_NOT_USED = JavaErrorMessages.message("local.class.is.not.used");

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PostHighlightingPass");
  private final Project myProject;
  private final RefCountHolder myRefCountHolder;
  private final PsiFile myFile;
  @Nullable private final Editor myEditor;
  private final Document myDocument;
  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myCompiled;

  private Collection<HighlightInfo> myHighlights;
  private boolean myHasRedundantImports;
  private final CodeStyleManagerEx myStyleManager;
  private int myCurentEntryIndex;
  private boolean myHasMissortedImports;
  private ImplicitUsageProvider[] myImplicitUsageProviders;

  public PostHighlightingPass(Project project,
                              PsiFile file,
                              @Nullable Editor editor,
                              Document document,
                              int startOffset,
                              int endOffset,
                              boolean isCompiled) {
    super(document);
    myProject = project;
    myFile = file;
    myEditor = editor;
    myDocument = document;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myCompiled = isCompiled;

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    myRefCountHolder = daemonCodeAnalyzer.getFileStatusMap().getRefCountHolder(document, myFile);
    myStyleManager = (CodeStyleManagerEx)CodeStyleManager.getInstance(myProject);
    myCurentEntryIndex = -1;

    myImplicitUsageProviders = ApplicationManager.getApplication().getComponents(ImplicitUsageProvider.class);
  }

  public PostHighlightingPass(Project project,
                              PsiFile file,
                              @NotNull Editor editor,
                              int startOffset,
                              int endOffset,
                              boolean isCompiled) {
    this(project, file, editor, editor.getDocument(), startOffset, endOffset, isCompiled);
  }

  public PostHighlightingPass(Project project,
                              PsiFile file,
                              Document document,
                              int startOffset,
                              int endOffset,
                              boolean isCompiled) {
    this(project, file, null, document, startOffset, endOffset, isCompiled);
  }

  public void doCollectInformation(ProgressIndicator progress) {
    if (myCompiled) {
      myHighlights = Collections.emptyList();
      return;
    }

    List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getRelevantLanguages();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if(!HighlightUtil.shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      collectHighlights(elements, highlights);
    }

    PsiNamedElement[] unusedDcls = myRefCountHolder.getUnusedDcls();
    for (PsiNamedElement unusedDcl : unusedDcls) {
      String dclType = UsageViewUtil.capitalize(UsageViewUtil.getType(unusedDcl));
      if (dclType == null || dclType.length() == 0) dclType = LangBundle.message("java.terms.symbol");
      String message = MessageFormat.format(SYMBOL_IS_NOT_USED, dclType, unusedDcl.getName());

      HighlightInfo highlightInfo = createUnusedSymbolInfo(unusedDcl.getNavigationElement(), message);
      highlights.add(highlightInfo);
    }

    myHighlights = highlights;
  }

  public void doApplyInformationToEditor() {
    if (myHighlights == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                   myHighlights, UpdateHighlightersUtil.POST_HIGHLIGHTERS_GROUP);

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, FileStatusMap.NORMAL_HIGHLIGHTERS);

    if (timeToOptimizeImports() && myEditor != null) {
      optimizeImportsOnTheFly();
    }
    //Q: here?
    ErrorStripeRenderer renderer = new RefreshStatusRenderer(myProject, daemonCodeAnalyzer, myDocument, myFile);
    Editor[] editors = EditorFactory.getInstance().getEditors(myDocument, myProject);
    for (Editor editor : editors) {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(renderer);
    }
  }

  private void optimizeImportsOnTheFly() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                OptimizeImportsFix optimizeImportsFix = new OptimizeImportsFix();
                if ((myHasRedundantImports || myHasMissortedImports)
                    && optimizeImportsFix.isAvailable(myProject, myEditor, myFile)
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

  public int getPassId() {
    return Pass.POST_UPDATE_ALL;
  }

  // for tests only
  public Collection<HighlightInfo> getHighlights() {
    return myHighlights;
  }

  private void collectHighlights(List<PsiElement> elements, List<HighlightInfo> array) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      if (element instanceof PsiIdentifier) {
        final HighlightInfo highlightInfo = processIdentifier((PsiIdentifier)element);
        if (highlightInfo != null) array.add(highlightInfo);
      }
      else if (element instanceof PsiImportList) {
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

  private HighlightInfo processIdentifier(PsiIdentifier identifier) {
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(identifier);
    if (!profile.isToolEnabled(HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME))) return null;
    final UnusedSymbolLocalInspection unusedSymbolInspection = (UnusedSymbolLocalInspection)((LocalInspectionToolWrapper)profile.getInspectionTool(UnusedSymbolLocalInspection.SHORT_NAME)).getTool();
    if (InspectionManagerEx.inspectionResultSuppressed(identifier, unusedSymbolInspection.getID())) return null;
    PsiElement parent = identifier.getParent();
    if (PsiUtil.hasErrorElementChild(parent)) return null;
    List<IntentionAction> options = IntentionManager.getInstance(myProject).getStandardIntentionOptions(HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME), identifier);
    String displayName  = UnusedSymbolLocalInspection.DISPLAY_NAME;
    HighlightInfo info;

    if (parent instanceof PsiLocalVariable && unusedSymbolInspection.LOCAL_VARIABLE) {
      info = processLocalVariable((PsiLocalVariable)parent, options, displayName);
    }
    else if (parent instanceof PsiField && unusedSymbolInspection.FIELD) {
      info = processField((PsiField)parent, options, displayName);
    }
    else if (parent instanceof PsiParameter && unusedSymbolInspection.PARAMETER) {
      info = processParameter((PsiParameter)parent, options, displayName);
    }
    else if (parent instanceof PsiMethod && unusedSymbolInspection.METHOD) {
      info = processMethod((PsiMethod)parent, options, displayName);
    }
    else if (parent instanceof PsiClass && identifier.equals(((PsiClass)parent).getNameIdentifier()) && unusedSymbolInspection.CLASS) {
      info = processClass((PsiClass)parent, options, displayName);
    }
    else {
      return null;
    }
    return info;
  }

  private HighlightInfo processLocalVariable(PsiLocalVariable variable, final List<IntentionAction> options, final String displayName) {
    PsiIdentifier identifier = variable.getNameIdentifier();
    if (identifier == null) return null;

    if (!myRefCountHolder.isReferenced(variable) && !isImplicitUsage(variable)) {
      String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_USED, identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), options, displayName);
      return highlightInfo;
    }

    boolean referenced = myRefCountHolder.isReferencedForRead(variable);
    if (!referenced && !isImplicitRead(variable)) {
      String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_USED_FOR_READING, identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), options, displayName);
      return highlightInfo;
    }

    if (!variable.hasInitializer()) {
      referenced = myRefCountHolder.isReferencedForWrite(variable);
      if (!referenced && !isImplicitWrite(variable)) {
        String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_ASSIGNED, identifier.getText());
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(UnusedSymbolLocalInspection.DISPLAY_NAME, options), options, displayName);
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
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    return HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_SYMBOL, element, message, attributes);
  }

  private HighlightInfo processField(PsiField field, final List<IntentionAction> options, final String displayName) {
    final PsiIdentifier identifier = field.getNameIdentifier();
    final boolean injected = field.getModifierList().findAnnotation("javax.annotation.Resource") != null ||
                             field.getModifierList().findAnnotation("javax.ejb.EJB") != null ||
                             field.getModifierList().findAnnotation("javax.xml.ws.WebServiceRef") != null;

    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(field) && !isImplicitUsage(field)) {
        if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
          return null;
        }
        String message = MessageFormat.format(JavaErrorMessages.message("private.field.is.not.used"), identifier.getText());
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), options, displayName);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, false, field), options, displayName);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(false, true, field), options, displayName);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, true, field), options, displayName);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateConstructorParameterFromFieldFix(field), options, displayName);
        return highlightInfo;
      }

      final boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced && !isImplicitRead(field)) {
        String message = MessageFormat.format(PRIVATE_FIELD_IS_NOT_USED_FOR_READING, identifier.getText());
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), options, displayName);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, false, field), options, displayName);
        return highlightInfo;
      }

      if (!field.hasInitializer()) {
        final boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
        if (!writeReferenced && !injected && !isImplicitWrite(field)) {
          String message = MessageFormat.format(JavaErrorMessages.message("private.field.is.not.assigned"), identifier.getText());
          HighlightInfo info = createUnusedSymbolInfo(identifier, message);
          QuickFixAction.registerQuickFixAction(info, new CreateGetterOrSetterAction(false, true, field), options, displayName);
          QuickFixAction.registerQuickFixAction(info, new CreateConstructorParameterFromFieldFix(field), options, displayName);
          return info;
        }
      }
    }

    return null;
  }

  private HighlightInfo processParameter(PsiParameter parameter, final List<IntentionAction> options, final String displayName) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
      if (PsiUtil.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC))
          && !method.hasModifierProperty(PsiModifier.NATIVE)
          && !HighlightMethodUtil.isSerializationRelatedMethod(method)
          && !isMainMethod(method)
      ) {
        if (!myRefCountHolder.isReferenced(parameter) && !isImplicitUsage(parameter)) {
          PsiIdentifier identifier = parameter.getNameIdentifier();
          String message = MessageFormat.format(PARAMETER_IS_NOT_USED, identifier.getText());
          HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
          QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedParameterFix(parameter), options, displayName);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      if (!myRefCountHolder.isReferenced(parameter) && !isImplicitUsage(parameter)) {
        PsiIdentifier identifier = parameter.getNameIdentifier();
        String message = MessageFormat.format(PARAMETER_IS_NOT_USED, identifier.getText());
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(UnusedSymbolLocalInspection.DISPLAY_NAME, options), options, displayName);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  private HighlightInfo processMethod(PsiMethod method, final List<IntentionAction> options, final String displayName) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(method)) {
        if (HighlightMethodUtil.isSerializationRelatedMethod(method) ||
            isIntentionalPrivateConstructor(method) ||
            isImplicitUsage(method)
        ) {
          return null;
        }
        String pattern = method.isConstructor() ? PRIVATE_CONSTRUCTOR_IS_NOT_USED : PRIVATE_METHOD_IS_NOT_USED;
        String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
        String message = MessageFormat.format(pattern, symbolName);
        PsiIdentifier identifier = method.getNameIdentifier();
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(method), options, displayName);
        return highlightInfo;
      }
    }
    return null;
  }

  private HighlightInfo processClass(PsiClass aClass, final List<IntentionAction> options, final String displayName) {
    if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(aClass) && !isImplicitUsage(aClass)) {
        String pattern = aClass.isInterface()
                         ? PRIVATE_INNER_INTERFACE_IS_NOT_USED
                         : PRIVATE_INNER_CLASS_IS_NOT_USED;
        return formatUnusedSymbolHighlightInfo(aClass, pattern, options, displayName);
      }
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
      if (!myRefCountHolder.isReferenced(aClass) && !isImplicitUsage(aClass)) {
        return formatUnusedSymbolHighlightInfo(aClass, LOCAL_CLASS_IS_NOT_USED, options, displayName);
      }
    }
    else if (aClass instanceof PsiTypeParameter) {
      if (!myRefCountHolder.isReferenced(aClass) && !isImplicitUsage(aClass)) {
        return formatUnusedSymbolHighlightInfo(aClass, TYPE_PARAMETER_IS_NOT_USED, options, displayName);
      }
    }
    return null;
  }

  private static HighlightInfo formatUnusedSymbolHighlightInfo(PsiClass aClass,
                                                               String pattern,
                                                               final List<IntentionAction> options,
                                                               final String displayName) {
    String symbolName = aClass.getName();
    String message = MessageFormat.format(pattern, symbolName);
    PsiIdentifier identifier = aClass.getNameIdentifier();
    HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(aClass), options, displayName);
    return highlightInfo;
  }

  private HighlightInfo processImport(PsiImportStatementBase importStatement) {
    if (!InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile(importStatement).isToolEnabled(HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME))) return null;

    // jsp include directive hack
    if (importStatement instanceof JspxImportStatement && ((JspxImportStatement)importStatement).isForeignFileImport()) return null;

    if (PsiUtil.hasErrorElementChild(importStatement)) return null;

    boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
    if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
      //check import from same package
      String packageName = ((PsiJavaFile)importStatement.getContainingFile()).getPackageName();
      PsiElement resolved = importStatement.getImportReference().resolve();
      if (resolved instanceof PsiPackage) {
        isRedundant = packageName.equals(((PsiPackage)resolved).getQualifiedName());
      }
      else if (resolved instanceof PsiClass) {
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
    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_IMPORT,
                                                           importStatement,
                                                           InspectionsBundle.message("unused.import.statement"));
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new EditInspectionToolsSettingsAction(HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME)));
    options.add(new DisableInspectionToolAction(HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME)));
    String displayName = UnusedImportLocalInspection.DISPLAY_NAME;
    QuickFixAction.registerQuickFixAction(info, new OptimizeImportsFix(), options, displayName);
    QuickFixAction.registerQuickFixAction(info, new EnableOptimizeImportsOnTheFlyFix(), options, displayName);
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
    HighlightInfo[] errors = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.ERROR, myProject);
    if (errors.length != 0) return false;

    return DaemonCodeAnalyzerImpl.canChangeFileSilently(myFile);
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
    if (method.getParameterList().getParameters().length > 0) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass != null && aClass.getConstructors().length == 1;
  }
}
