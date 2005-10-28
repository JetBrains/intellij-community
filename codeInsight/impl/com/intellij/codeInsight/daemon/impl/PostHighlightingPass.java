package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.LangBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PostHighlightingPass extends TextEditorHighlightingPass {
  private static final String SYMBOL_IS_NOT_USED = JavaErrorMessages.message("symbol.is.never.used");
  private static final String LOCAL_VARIABLE_IS_NOT_USED = JavaErrorMessages.message("local.variable.is.never.used");
  private static final String LOCAL_VARIABLE_IS_NOT_USED_FOR_READING = JavaErrorMessages.message("local.variable.is.not.used.for.reading");
  private static final String LOCAL_VARIABLE_IS_NOT_ASSIGNED = JavaErrorMessages.message("local.variable.is.not.assigned");
  private static final String PRIVATE_FIELD_IS_NOT_USED = JavaErrorMessages.message("private.field.is.not.used");
  private static final String PRIVATE_FIELD_IS_NOT_USED_FOR_READING = JavaErrorMessages.message("private.field.is.not.used.for.reading");
  private static final String PRIVATE_FIELD_IS_NOT_ASSIGNED = JavaErrorMessages.message("private.field.is.not.assigned");
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
  private DaemonCodeAnalyzerSettings mySettings = DaemonCodeAnalyzerSettings.getInstance();
  private boolean myHasRedundantImports;
  private final CodeStyleManagerEx myStyleManager;
  private int myCurentEntryIndex;
  private boolean myHasMissortedImports;
  private static final @NonNls String WRITE_OBJECT_METHOD = "writeObject";
  private static final @NonNls String WRITE_REPLACE_METHOD = "writeReplace";
  private static final @NonNls String READ_RESOLVE_METHOD = "readResolve";
  private static final @NonNls String READ_OBJECT_METHOD = "readObject";
  private static final @NonNls String READ_OBJECT_NO_DATA_METHOD = "readObjectNoData";

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
    myStyleManager = (CodeStyleManagerEx)CodeStyleManagerEx.getInstance(myProject);
    myCurentEntryIndex = -1;
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

    PsiElement[] psiRoots = myFile.getPsiRoots();
    List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();

    for (final PsiElement psiRoot : psiRoots) {
      if(!HighlightUtil.isRootHighlighted(psiRoot)) continue;
      PsiElement[] elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      collectHighlights(elements, highlights);
    }

    PsiNamedElement[] unusedDcls = myRefCountHolder.getUnusedDcls();
    for (PsiNamedElement unusedDcl : unusedDcls) {
      String dclType = UsageViewUtil.capitalize(UsageViewUtil.getType(unusedDcl));
      if (dclType == null || dclType.length() == 0) dclType = LangBundle.message("java.terms.symbol");
      String message = MessageFormat.format(SYMBOL_IS_NOT_USED, dclType, unusedDcl.getName());

      HighlightInfo highlightInfo = createUnusedSymbolInfo(unusedDcl.getNavigationElement(), dclType + message);
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

  private void collectHighlights(PsiElement[] elements, List<HighlightInfo> array) throws ProcessCanceledException {
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
    InspectionProfileImpl profile = mySettings.getInspectionProfile(identifier);
    if (!profile.isToolEnabled(HighlightDisplayKey.UNUSED_SYMBOL)) return null;
    if (InspectionManagerEx.inspectionResultSuppressed(identifier, HighlightDisplayKey.UNUSED_SYMBOL.getID())) return null;
    PsiElement parent = identifier.getParent();
    if (PsiUtil.hasErrorElementChild(parent)) return null;
    List<IntentionAction> options = IntentionManager.getInstance(myProject).getStandardIntentionOptions(HighlightDisplayKey.UNUSED_SYMBOL, identifier);
    HighlightInfo info;
    InspectionProfile.UnusedSymbolSettings unusedSymbolSettings = profile.getUnusedSymbolSettings();
    if (parent instanceof PsiLocalVariable && unusedSymbolSettings.LOCAL_VARIABLE) {
      info = processLocalVariable((PsiLocalVariable)parent, options);
    }
    else if (parent instanceof PsiField && unusedSymbolSettings.FIELD) {
      info = processField((PsiField)parent, options);
    }
    else if (parent instanceof PsiParameter && unusedSymbolSettings.PARAMETER) {
      info = processParameter((PsiParameter)parent, options);
    }
    else if (parent instanceof PsiMethod && unusedSymbolSettings.METHOD) {
      info = processMethod((PsiMethod)parent, options);
    }
    else if (parent instanceof PsiClass && identifier.equals(((PsiClass)parent).getNameIdentifier()) && unusedSymbolSettings.CLASS) {
      info = processClass((PsiClass)parent, options);
    }
    else {
      return null;
    }
    return info;
  }

  private HighlightInfo processLocalVariable(PsiLocalVariable variable, final List<IntentionAction> options) {
    PsiIdentifier identifier = variable.getNameIdentifier();

    if (!myRefCountHolder.isReferenced(variable)) {
      String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_USED, identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), options);
      return highlightInfo;
    }

    boolean referenced = myRefCountHolder.isReferencedForRead(variable);
    if (!referenced) {
      String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_USED_FOR_READING, identifier.getText());
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), options);
      return highlightInfo;
    }

    if (!variable.hasInitializer()) {
      referenced = myRefCountHolder.isReferencedForWrite(variable);
      if (!referenced) {
        String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_ASSIGNED, identifier.getText());
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.UNUSED_SYMBOL), options), options);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  private static HighlightInfo createUnusedSymbolInfo(PsiElement element, String message) {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    return HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_SYMBOL, element.getTextRange(), message, attributes);
  }

  private HighlightInfo processField(PsiField field, final List<IntentionAction> options) {
    final PsiIdentifier identifier = field.getNameIdentifier();
    final PsiFile boundForm = CodeInsightUtil.getFormFile(field);
    final boolean isBoundToForm = boundForm != null;

    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(field)) {
        if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
          return null;
        }
        String message = MessageFormat.format(PRIVATE_FIELD_IS_NOT_USED, identifier.getText());
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, false, field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(false, true, field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, true, field), options);
        return highlightInfo;
      }

      final boolean readReferenced = myRefCountHolder.isReferencedForRead(field);
      if (!readReferenced) {
        String message = MessageFormat.format(PRIVATE_FIELD_IS_NOT_USED_FOR_READING, identifier.getText());
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, false, field), options);
        return highlightInfo;
      }

      if (!field.hasInitializer()) {
        final boolean writeReferenced = myRefCountHolder.isReferencedForWrite(field);
        if (!writeReferenced && !isBoundToForm) {
          String message = MessageFormat.format(PRIVATE_FIELD_IS_NOT_ASSIGNED, identifier.getText());
          HighlightInfo info = createUnusedSymbolInfo(identifier, message);
          QuickFixAction.registerQuickFixAction(info, new CreateGetterOrSetterAction(false, true, field), options);
          return info;
        }
      }
    }

    return null;
  }

  private HighlightInfo processParameter(PsiParameter parameter, final List<IntentionAction> options) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
      if (PsiUtil.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE) ||
           method.hasModifierProperty(PsiModifier.STATIC))
          && !method.hasModifierProperty(PsiModifier.NATIVE)
      ) {
        if (isMainMethod(method)) return null;
        if (!myRefCountHolder.isReferenced(parameter)) {
          PsiIdentifier identifier = parameter.getNameIdentifier();
          String message = MessageFormat.format(PARAMETER_IS_NOT_USED, identifier.getText());
          HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
          QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedParameterFix(parameter), options);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      if (!myRefCountHolder.isReferenced(parameter)) {
        PsiIdentifier identifier = parameter.getNameIdentifier();
        String message = MessageFormat.format(PARAMETER_IS_NOT_USED, identifier.getText());
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.UNUSED_SYMBOL), options), options);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  private HighlightInfo processMethod(PsiMethod method, final List<IntentionAction> options) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(method)) {
        if (isWriteObjectMethod(method) ||
            isWriteReplaceMethod(method) ||
            isReadObjectMethod(method) ||
            isReadObjectNoDataMethod(method) ||
            isReadResolveMethod(method) ||
            isIntentionalPrivateConstructor(method)
        ) {
          return null;
        }
        String pattern = method.isConstructor() ? PRIVATE_CONSTRUCTOR_IS_NOT_USED : PRIVATE_METHOD_IS_NOT_USED;
        String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
        String message = MessageFormat.format(pattern, symbolName);
        PsiIdentifier identifier = method.getNameIdentifier();
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(method), options);
        return highlightInfo;
      }
    }
    return null;
  }

  private HighlightInfo processClass(PsiClass aClass, final List<IntentionAction> options) {
    if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      if (!myRefCountHolder.isReferenced(aClass)) {
        String pattern = aClass.isInterface()
                         ? PRIVATE_INNER_INTERFACE_IS_NOT_USED
                         : PRIVATE_INNER_CLASS_IS_NOT_USED;
        return formatUnusedSymbolHighlightInfo(aClass, pattern, options);
      }
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
      if (!myRefCountHolder.isReferenced(aClass)) {
        return formatUnusedSymbolHighlightInfo(aClass, LOCAL_CLASS_IS_NOT_USED, options);
      }
    }
    else if (aClass instanceof PsiTypeParameter) {
      if (!myRefCountHolder.isReferenced(aClass)) {
        return formatUnusedSymbolHighlightInfo(aClass, TYPE_PARAMETER_IS_NOT_USED, options);
      }
    }
    return null;
  }

  private static HighlightInfo formatUnusedSymbolHighlightInfo(PsiClass aClass, String pattern, final List<IntentionAction> options) {
    String symbolName = aClass.getName();
    String message = MessageFormat.format(pattern, symbolName);
    PsiIdentifier identifier = aClass.getNameIdentifier();
    HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(aClass), options);
    return highlightInfo;
  }

  private HighlightInfo processImport(PsiImportStatementBase importStatement) {
    if (!mySettings.getInspectionProfile(importStatement).isToolEnabled(HighlightDisplayKey.UNUSED_IMPORT)) return null;

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
    options.add(new EditInspectionToolsSettingsAction(HighlightDisplayKey.UNUSED_IMPORT));
    QuickFixAction.registerQuickFixAction(info, new OptimizeImportsFix(), options);
    QuickFixAction.registerQuickFixAction(info, new EnableOptimizeImportsOnTheFlyFix(), options);
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

    return !fileHasUnchangedStatus();
  }

  private boolean fileHasUnchangedStatus() {
    VirtualFile virtualFile = myFile.getVirtualFile();
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    if (activeVcs == null) return false;
    FileStatus status = FileStatusManager.getInstance(myProject).getStatus(virtualFile);

    return status == FileStatus.NOT_CHANGED;
  }

  private static boolean isWriteObjectMethod(PsiMethod method) {
    if (!WRITE_OBJECT_METHOD.equals(method.getName())) return false;
    PsiType returnType = method.getReturnType();
    if (!TypeConversionUtil.isVoidType(returnType)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectOutputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass == null || HighlightUtil.isSerializable(aClass);
  }

  private static boolean isWriteReplaceMethod(PsiMethod method) {
    if (!WRITE_REPLACE_METHOD.equals(method.getName())) return false;
    PsiType returnType = method.getReturnType();
    if (returnType == null || !returnType.equalsToText("java.lang.Object")) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass == null || HighlightUtil.isSerializable(aClass);
  }

  private static boolean isReadResolveMethod(PsiMethod method) {
    if (!READ_RESOLVE_METHOD.equals(method.getName())) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiType returnType = method.getReturnType();
    if (returnType == null || !returnType.equalsToText("java.lang.Object")) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass == null || HighlightUtil.isSerializable(aClass);
  }

  private static boolean isReadObjectMethod(PsiMethod method) {
    if (!READ_OBJECT_METHOD.equals(method.getName())) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;

    if (!parameters[0].getType().equalsToText("java.io.ObjectInputStream")) return false;

    PsiType returnType = method.getReturnType();
    if (!TypeConversionUtil.isVoidType(returnType)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass == null || HighlightUtil.isSerializable(aClass);
  }

  private static boolean isReadObjectNoDataMethod(PsiMethod method) {
    if (!READ_OBJECT_NO_DATA_METHOD.equals(method.getName())) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    PsiType returnType = method.getReturnType();
    if (!TypeConversionUtil.isVoidType(returnType)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    return aClass == null || HighlightUtil.isSerializable(aClass);
  }

  private static boolean isMainMethod(PsiMethod method) {
    if (!PsiType.VOID.equals(method.getReturnType())) return false;
    PsiElementFactory factory = method.getManager().getElementFactory();
    try {
      PsiMethod appMain = factory.createMethodFromText("void main(String[] args);", null);
      if (MethodSignatureUtil.areSignaturesEqual(method, appMain)) return true;
      PsiMethod appPremain = factory.createMethodFromText("void premain(String[] args, java.lang.instrument.Instrumentation i);", null);
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
    if (aClass == null) return false;
    return aClass.getConstructors().length == 1;
  }
}
