// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MethodReturnTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private static final Logger LOG = Logger.getInstance(MethodReturnTypeFix.class);

  private final SmartTypePointer myReturnTypePointer;
  private final boolean myFixWholeHierarchy;
  private final boolean mySuggestSuperTypes;
  private final String myName;
  private final String myCanonicalText;

  public MethodReturnTypeFix(@NotNull PsiMethod method, @NotNull PsiType returnType, boolean fixWholeHierarchy) {
    this(method, returnType, fixWholeHierarchy, false);
  }

  public MethodReturnTypeFix(@NotNull PsiMethod method, @NotNull PsiType returnType, boolean fixWholeHierarchy, boolean suggestSuperTypes) {
    super(method);
    myReturnTypePointer = SmartTypePointerManager.getInstance(method.getProject()).createSmartTypePointer(returnType);
    myFixWholeHierarchy = fixWholeHierarchy;
    mySuggestSuperTypes = suggestSuperTypes;
    myName = method.getName();
    if (TypeConversionUtil.isNullType(returnType)) {
      returnType = PsiType.getJavaLangObject(method.getManager(), method.getResolveScope());
    }
    if (fixWholeHierarchy) {
      PsiType type = getHierarchyAdjustedReturnType(method, returnType);
      myCanonicalText = (type != null ? type : returnType).getCanonicalText();
    }
    else {
      myCanonicalText = returnType.getCanonicalText();
    }
  }


  @NotNull
  @Override
  public String getText() {
    if (!mySuggestSuperTypes) return QuickFixBundle.message("fix.return.type.text", myName, myCanonicalText);
    PsiType type = Objects.requireNonNull(myReturnTypePointer.getType());
    boolean hasPredecessor = type.getSuperTypes().length != 0;
    return QuickFixBundle.message(hasPredecessor ? "fix.return.type.or.predecessor.text" : "fix.return.type.text", myName, myCanonicalText);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.return.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;

    final PsiType myReturnType = myReturnTypePointer.getType();
    if (BaseIntentionAction.canModify(myMethod) &&
        myReturnType != null &&
        myReturnType.isValid()) {
      final PsiType returnType = myMethod.getReturnType();
      if (returnType == null) return true;
      if (returnType.isValid() && !Comparing.equal(myReturnType, returnType)) {
        return PsiTypesUtil.allTypeParametersResolved(myMethod, myReturnType);
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;

    if (!FileModificationService.getInstance().prepareFileForWrite(myMethod.getContainingFile())) return;
    PsiType returnType = myReturnTypePointer.getType();
    if (returnType == null) return;
    boolean isNullType = TypeConversionUtil.isNullType(returnType);
    PsiType myReturnType = isNullType ? PsiType.getJavaLangObject(myMethod.getManager(), myMethod.getResolveScope()) : returnType;
    PsiTypeElement typeElement = myMethod.getReturnTypeElement();
    if (typeElement == null) {
      WriteCommandAction.runWriteCommandAction(project, QuickFixBundle.message("fix.return.type.family"), null,
                                               () -> addReturnType(project, myMethod, myReturnType));
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    }
    PsiType[] superTypes = mySuggestSuperTypes ? myReturnType.getSuperTypes() : PsiType.EMPTY_ARRAY;
    if ((!isNullType && superTypes.length == 0) || editor == null || ApplicationManager.getApplication().isUnitTestMode()) {
      changeReturnType(project, file, editor, myMethod, myReturnType);
      return;
    }
    List<PsiType> returnTypes = getReturnTypes(superTypes, myReturnType);
    if (returnTypes.isEmpty()) return;
    selectReturnType(project, file, editor, returnTypes, myReturnType, myMethod);
  }

  private static void addReturnType(@NotNull Project project, @NotNull PsiMethod myMethod, @NotNull PsiType myReturnType) {
    PsiTypeElement typeElement = PsiElementFactory.getInstance(project).createTypeElement(myReturnType);
    myMethod.addBefore(typeElement, myMethod.getNameIdentifier());
  }

  @NotNull
  private static List<PsiType> getReturnTypes(PsiType @NotNull [] types, @NotNull PsiType defaultType) {
    Map<String, PsiType> map = new HashMap<>();
    String defaultTypeKey = serialize(defaultType);
    map.put(defaultTypeKey, defaultType);
    Arrays.stream(types).forEach(t -> map.put(serialize(t), t));

    List<PsiType> ordered = new ArrayList<>();
    StatisticsManager statisticsManager = StatisticsManager.getInstance();
    String statsKey = "IntroduceVariable##" + defaultTypeKey;
    for (StatisticsInfo info : statisticsManager.getAllValues(statsKey)) {
      String typeKey = info.getValue();
      PsiType type = map.get(typeKey);
      if (type != null) {
        map.remove(typeKey);
        ordered.add(type);
      }
    }
    ordered.addAll(map.values());
    return ordered;
  }

  @NotNull
  private static String serialize(PsiType type) {
    if (PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter) return type.getCanonicalText();
    return TypeConversionUtil.erasure(type).getCanonicalText();
  }

  void selectReturnType(@NotNull Project project,
                        @NotNull PsiFile file,
                        @NotNull Editor editor,
                        @NotNull List<PsiType> returnTypes,
                        @NotNull PsiType myReturnType,
                        @NotNull PsiMethod myMethod) {
    PsiTypeElement typeElement = myMethod.getReturnTypeElement();
    if (typeElement == null) return;
    TemplateBuilderImpl builder = new TemplateBuilderImpl(typeElement);
    builder.replaceElement(typeElement, new TypeExpression(project, returnTypes));
    Template template = WriteCommandAction.runWriteCommandAction(project, (Computable<Template>)() -> builder.buildInlineTemplate());
    TemplateEditingAdapter listener = new TemplateEditingAdapter() {
      @Override
      public void templateFinished(@NotNull Template template, boolean brokenOff) {
        if (brokenOff) return;
        PsiType newReturnType = myMethod.getReturnType();
        if (newReturnType == null) return;
        TypeSelectorManagerImpl.typeSelected(newReturnType, myReturnType);
        changeReturnType(project, file, editor, myMethod, newReturnType);
      }
    };
    editor.getCaretModel().moveToOffset(typeElement.getTextOffset());
    TemplateManager.getInstance(project).startTemplate(editor, template, listener);
  }

  private void changeReturnType(@NotNull Project project,
                                @NotNull PsiFile file,
                                Editor editor,
                                @NotNull PsiMethod myMethod,
                                @NotNull PsiType myReturnType) {
    if (myFixWholeHierarchy) {
      final PsiMethod superMethod = myMethod.findDeepestSuperMethod();
      final PsiType superReturnType = superMethod == null ? null : superMethod.getReturnType();
      if (superReturnType != null &&
          !Comparing.equal(myReturnType, superReturnType) &&
          !changeClassTypeArgument(myMethod, project, superReturnType, superMethod.getContainingClass(), editor, myReturnType)) {
        return;
      }
    }

    final List<PsiMethod> affectedMethods = changeReturnType(myMethod, myReturnType);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiReturnStatement statementToSelect = null;
    if (!PsiType.VOID.equals(myReturnType)) {
      final ReturnStatementAdder adder = new ReturnStatementAdder(factory, myReturnType);

      for (PsiMethod affectedMethod : affectedMethods) {
        PsiReturnStatement statement = adder.addReturnForMethod(file, affectedMethod);
        if (statement != null && affectedMethod == myMethod) {
          statementToSelect = statement;
        }
      }
    }

    if (statementToSelect != null) {
      Editor editorForMethod = getEditorForMethod(myMethod, project, editor, file);
      if (editorForMethod != null) {
        selectInEditor(statementToSelect.getReturnValue(), editorForMethod);
      }
    }
  }

  // to clearly separate data
  private static final class ReturnStatementAdder {
    @NotNull private final PsiElementFactory factory;
    @NotNull private final PsiType myTargetType;

    private ReturnStatementAdder(@NotNull final PsiElementFactory factory, @NotNull final PsiType targetType) {
      this.factory = factory;
      myTargetType = targetType;
    }

    private PsiReturnStatement addReturnForMethod(final PsiFile file, final PsiMethod method) {
      final PsiModifierList modifiers = method.getModifierList();
      if (modifiers.hasModifierProperty(PsiModifier.ABSTRACT) || method.getBody() == null) {
        return null;
      }

      try {
        final ConvertReturnStatementsVisitor visitor = new ConvertReturnStatementsVisitor(factory, method, myTargetType);

        ControlFlow controlFlow;
        try {
          controlFlow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(method.getBody());
        }
        catch (AnalysisCanceledException e) {
          return null; //must be an error
        }
        PsiReturnStatement returnStatement;
        if (ControlFlowUtil.processReturns(controlFlow, visitor)) {
          // extra return statement not needed
          // get latest modified return statement and select...
          returnStatement = visitor.getLatestReturn();
        }
        else {
          returnStatement = visitor.createReturnInLastStatement();
        }
        if (method.getContainingFile() != file) {
          UndoUtil.markPsiFileForUndo(file);
        }
        return returnStatement;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      return null;
    }
  }

  private static Editor getEditorForMethod(PsiMethod myMethod, @NotNull final Project project, final Editor editor, final PsiFile file) {

    PsiFile containingFile = myMethod.getContainingFile();
    if (containingFile != file) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, containingFile.getVirtualFile());
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    return editor;
  }

  private static PsiType getHierarchyAdjustedReturnType(final PsiMethod method, @NotNull PsiType returnType) {
    for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
      PsiType superMethodReturnType = superMethod.getReturnType();
      if (superMethodReturnType != null && superMethodReturnType.isAssignableFrom(returnType)) {
        if (superMethodReturnType instanceof PsiClassType && returnType instanceof PsiPrimitiveType) {
          return ((PsiPrimitiveType)returnType).getBoxedType(method);
        }
        return returnType;
      }
    }
    return null;
  }

  @NotNull
  private List<PsiMethod> changeReturnType(final PsiMethod method, @NotNull PsiType returnType) {
    PsiMethod[] methods = new PsiMethod[] {method};
    if (myFixWholeHierarchy) {
      PsiType type = getHierarchyAdjustedReturnType(method, returnType);
      if (type != null) {
        returnType = type;
      }
      else {
        final PsiMethod[] superMethods = method.findDeepestSuperMethods();
        if (superMethods.length > 0) {
          methods = superMethods;
        }
      }
    }

    Project project = method.getProject();
    final List<PsiMethod> affectedMethods = new ArrayList<>();
    for (PsiMethod targetMethod : methods) {
      affectedMethods.add(targetMethod);
      var provider = JavaSpecialRefactoringProvider.getInstance();
      var processor = provider.getChangeSignatureProcessor(project, targetMethod,
                                                           false, null,
                                                           myName,
                                                           returnType,
                                                           ParameterInfoImpl.fromMethod(targetMethod),
                                                           null);
      processor.run();
    }

    PsiMethod[] hierarchyMethods = methods;
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      for (PsiMethod psiMethod : hierarchyMethods) {
        OverridingMethodsSearch.search(psiMethod).forEach(m -> {
          affectedMethods.add(m);
        });
      }
    }, JavaBundle.message("progress.title.collect.method.overriders"), true, project)) {
      return Collections.emptyList();
    }
    return affectedMethods;
  }

  static void selectInEditor(@Nullable PsiElement element, Editor editor) {
    LOG.assertTrue(element != null);
    TextRange range = element.getTextRange();
    int offset = range.getStartOffset();

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(range.getEndOffset(), range.getStartOffset());
  }

  private static boolean changeClassTypeArgument(PsiMethod myMethod,
                                                 Project project,
                                                 PsiType superReturnType,
                                                 PsiClass superClass,
                                                 Editor editor, PsiType returnType) {
    if (superClass == null || !superClass.hasTypeParameters()) return true;
    final PsiClass superReturnTypeClass = PsiUtil.resolveClassInType(superReturnType);
    if (superReturnTypeClass == null || !(superReturnTypeClass instanceof PsiTypeParameter || superReturnTypeClass.hasTypeParameters())) return true;

    final PsiClass derivedClass = myMethod.getContainingClass();
    if (derivedClass == null) return true;

    final PsiReferenceParameterList referenceParameterList = findTypeArgumentsList(superClass, derivedClass);
    if (referenceParameterList == null) return true;

    final PsiElement resolve = ((PsiJavaCodeReferenceElement)referenceParameterList.getParent()).resolve();
    if (!(resolve instanceof PsiClass)) return true;
    final PsiClass baseClass = (PsiClass)resolve;

    if (returnType instanceof PsiPrimitiveType) {
      returnType = ((PsiPrimitiveType)returnType).getBoxedType(derivedClass);
    }

    final PsiSubstitutor superClassSubstitutor =
      TypeConversionUtil.getSuperClassSubstitutor(superClass, baseClass, PsiSubstitutor.EMPTY);
    final PsiType superReturnTypeInBaseClassType = superClassSubstitutor.substitute(superReturnType);
    if (superReturnTypeInBaseClassType == null) return true;
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
    final PsiSubstitutor psiSubstitutor =
      resolveHelper.inferTypeArguments(PsiTypesUtil.filterUnusedTypeParameters(superReturnTypeInBaseClassType, baseClass.getTypeParameters()),
                                       new PsiType[]{superReturnTypeInBaseClassType},
                                       new PsiType[]{returnType},
                                       PsiUtil.getLanguageLevel(superClass));

    final PsiSubstitutor compoundSubstitutor =
      TypeConversionUtil.getSuperClassSubstitutor(superClass, derivedClass, PsiSubstitutor.EMPTY).putAll(psiSubstitutor);
    var scope = new LocalSearchScope(derivedClass);
    var type = JavaPsiFacade.getElementFactory(project).createType(baseClass, compoundSubstitutor);
    JavaSpecialRefactoringProvider.getInstance().runHighlightingTypeMigration(project, editor, scope, referenceParameterList, type);

    return false;
  }

  @Nullable
  private static PsiReferenceParameterList findTypeArgumentsList(final PsiClass superClass, final PsiClass derivedClass) {
    PsiReferenceParameterList referenceParameterList = null;
    if (derivedClass instanceof PsiAnonymousClass) {
      referenceParameterList = ((PsiAnonymousClass)derivedClass).getBaseClassReference().getParameterList();
    } else {
      final PsiReferenceList implementsList = derivedClass.getImplementsList();
      if (implementsList != null) {
        referenceParameterList = extractReferenceParameterList(superClass, implementsList);
      }
      if (referenceParameterList == null) {
        final PsiReferenceList extendsList = derivedClass.getExtendsList();
        if (extendsList != null) {
          referenceParameterList = extractReferenceParameterList(superClass, extendsList);
        }
      }
    }
    return referenceParameterList;
  }

  @Nullable
  private static PsiReferenceParameterList extractReferenceParameterList(final PsiClass superClass,
                                                                         final PsiReferenceList extendsList) {
    for (PsiJavaCodeReferenceElement referenceElement : extendsList.getReferenceElements()) {
      final PsiElement element = referenceElement.resolve();
      if (element instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)element, superClass, true)) {
        return referenceElement.getParameterList();
      }
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
