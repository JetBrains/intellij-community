// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.highlighter.JavaFileType;
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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.*;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import one.util.streamex.Joining;
import one.util.streamex.StreamEx;
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
  private final String myDisplayName;

  public MethodReturnTypeFix(@NotNull PsiMethod method, @NotNull PsiType returnType, boolean fixWholeHierarchy) {
    this(method, returnType, fixWholeHierarchy, false, false);
  }

  public MethodReturnTypeFix(@NotNull PsiMethod method, @NotNull PsiType returnType, boolean fixWholeHierarchy, boolean suggestSuperTypes) {
    this(method, returnType, fixWholeHierarchy, suggestSuperTypes, false);
  }

  public MethodReturnTypeFix(@NotNull PsiMethod method, @NotNull PsiType returnType, boolean fixWholeHierarchy, boolean suggestSuperTypes,
                             boolean showClassName) {
    super(method);
    myDisplayName =
      PsiFormatUtil.formatMethod(
      method,
      PsiSubstitutor.EMPTY, showClassName ? PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS : PsiFormatUtilBase.SHOW_NAME,
      0
    );
    returnType = PsiTypesUtil.removeExternalAnnotations(returnType);
    myReturnTypePointer = SmartTypePointerManager.getInstance(method.getProject()).createSmartTypePointer(returnType);
    myFixWholeHierarchy = fixWholeHierarchy;
    mySuggestSuperTypes = suggestSuperTypes;
    myName = method.getName();
    returnType = correctType(method, returnType);
    if (fixWholeHierarchy) {
      PsiType type = getHierarchyAdjustedReturnType(method, returnType);
      myCanonicalText = (type != null ? type : returnType).getCanonicalText();
    }
    else {
      myCanonicalText = returnType.getCanonicalText();
    }
  }

  private static @NotNull PsiType correctType(@NotNull PsiMethod method, @NotNull PsiType returnType) {
    if (TypeConversionUtil.isNullType(returnType)) {
      returnType = PsiType.getJavaLangObject(method.getManager(), method.getResolveScope());
    }
    return returnType;
  }


  @Override
  public @NotNull String getText() {
    if (!mySuggestSuperTypes) return QuickFixBundle.message("fix.return.type.text", myDisplayName, myCanonicalText);
    PsiType type = Objects.requireNonNull(myReturnTypePointer.getType());
    boolean hasPredecessor = type.getSuperTypes().length != 0;
    return QuickFixBundle.message(hasPredecessor ? "fix.return.type.or.predecessor.text" : "fix.return.type.text",
                                  myDisplayName, myCanonicalText);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("fix.return.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiMethod method = (PsiMethod)startElement;
    if(!method.isPhysical()) return false;
    final PsiType newType = myReturnTypePointer.getType();
    if (BaseIntentionAction.canModify(method) && newType != null && newType.isValid()) {
      final PsiType returnType = method.getReturnType();
      if (returnType == null) return true;
      if (returnType.isValid() && !Comparing.equal(newType, returnType)) {
        if (!mySuggestSuperTypes && newType.getCanonicalText().equals(returnType.getCanonicalText())) return false;
        return PsiTypesUtil.allTypeParametersResolved(method, newType);
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiMethod method = (PsiMethod)startElement;

    if (!FileModificationService.getInstance().prepareFileForWrite(method.getContainingFile())) return;
    PsiType newType = myReturnTypePointer.getType();
    if (newType == null) return;
    boolean isNullType = TypeConversionUtil.isNullType(newType);
    PsiType returnType = isNullType ? PsiType.getJavaLangObject(method.getManager(), method.getResolveScope()) : newType;
    PsiTypeElement typeElement = method.getReturnTypeElement();
    if (typeElement == null) {
      WriteCommandAction.runWriteCommandAction(project, QuickFixBundle.message("fix.return.type.family"), null,
                                               () -> addReturnType(project, method, returnType));
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    }
    PsiType[] superTypes = mySuggestSuperTypes ? returnType.getSuperTypes() : PsiType.EMPTY_ARRAY;
    if ((!isNullType && superTypes.length == 0) || editor == null || ApplicationManager.getApplication().isUnitTestMode()) {
      changeReturnType(project, psiFile, editor, method, returnType);
    }
    else {
      Set<PsiType> allSuperTypes = collectSuperTypes(returnType, startElement, new LinkedHashSet<>());
      List<PsiType> returnTypes = getReturnTypes(allSuperTypes.toArray(PsiType.EMPTY_ARRAY), returnType);
      final PsiType currentType = method.getReturnType();
      assert currentType != null;
      returnTypes.removeIf(e -> e.getCanonicalText().equals(currentType.getCanonicalText()));
      if (returnTypes.isEmpty()) return;
      selectReturnType(project, psiFile, editor, returnTypes, returnType, method);
    }
  }

  private static Set<PsiType> collectSuperTypes(PsiType type, PsiElement context, Set<PsiType> result) {
    final PsiType[] superTypes = type.getSuperTypes();
    if (superTypes.length == 0 && type instanceof PsiArrayType) {
      final Project project = context.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      result.add(factory.createTypeFromText(CommonClassNames.JAVA_IO_SERIALIZABLE, context));
      result.add(factory.createTypeFromText(CommonClassNames.JAVA_LANG_CLONEABLE, context));
      result.add(factory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, context));
    }
    for (PsiType superType : superTypes) {
      if (result.add(superType)) collectSuperTypes(superType, context, result);
    }
    return result;
  }

  private static void addReturnType(@NotNull Project project, @NotNull PsiMethod myMethod, @NotNull PsiType myReturnType) {
    PsiTypeElement typeElement = PsiElementFactory.getInstance(project).createTypeElement(myReturnType);
    myMethod.addBefore(typeElement, myMethod.getNameIdentifier());
  }

  private static @NotNull List<PsiType> getReturnTypes(PsiType @NotNull [] types, @NotNull PsiType defaultType) {
    Map<String, PsiType> map = new LinkedHashMap<>();
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

  private static @NotNull String serialize(PsiType type) {
    if (PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter) return type.getCanonicalText();
    return TypeConversionUtil.erasure(type).getCanonicalText();
  }

  private void selectReturnType(@NotNull Project project,
                                @NotNull PsiFile psiFile,
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
        changeReturnType(project, psiFile, editor, myMethod, newReturnType);
      }
    };
    editor.getCaretModel().moveToOffset(typeElement.getTextOffset());
    TemplateManager.getInstance(project).startTemplate(editor, template, listener);
  }

  private void changeReturnType(@NotNull Project project,
                                @NotNull PsiFile psiFile,
                                Editor editor,
                                @NotNull PsiMethod method,
                                @NotNull PsiType returnType) {
    if (myFixWholeHierarchy) {
      final PsiMethod superMethod = method.findDeepestSuperMethod();
      final PsiType superReturnType = superMethod == null ? null : superMethod.getReturnType();
      if (superReturnType != null &&
          !Comparing.equal(returnType, superReturnType) &&
          !changeClassTypeArgument(method, project, superReturnType, superMethod.getContainingClass(), editor, returnType)) {
        return;
      }
    }

    final List<PsiMethod> affectedMethods = changeReturnType(method, returnType);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiReturnStatement statementToSelect = null;
    if (!PsiTypes.voidType().equals(returnType)) {
      final ReturnStatementAdder adder = new ReturnStatementAdder(factory, returnType);

      for (PsiMethod affectedMethod : affectedMethods) {
        PsiReturnStatement statement = adder.addReturnForMethod(psiFile, affectedMethod);
        if (statement != null && affectedMethod == method) {
          statementToSelect = statement;
        }
      }
    }

    if (statementToSelect != null) {
      Editor editorForMethod = getEditorForMethod(method, project, editor, psiFile);
      if (editorForMethod != null) {
        selectInEditor(statementToSelect.getReturnValue(), editorForMethod);
      }
    }
  }

  // to clearly separate data
  private static final class ReturnStatementAdder {
    private final @NotNull PsiElementFactory factory;
    private final @NotNull PsiType myTargetType;

    private ReturnStatementAdder(@NotNull PsiElementFactory factory, @NotNull PsiType targetType) {
      this.factory = factory;
      myTargetType = targetType;
    }

    private PsiReturnStatement addReturnForMethod(PsiFile psiFile, PsiMethod method) {
      final PsiModifierList modifiers = method.getModifierList();
      if (modifiers.hasModifierProperty(PsiModifier.ABSTRACT) || method.getBody() == null) {
        return null;
      }

      try {
        final ConvertReturnStatementsVisitor visitor = new ConvertReturnStatementsVisitor(factory, method, myTargetType);

        ControlFlow controlFlow;
        try {
          controlFlow = ControlFlowFactory.getControlFlowNoConstantEvaluate(method.getBody());
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
        if (method.getContainingFile() != psiFile) {
          UndoUtil.markPsiFileForUndo(psiFile);
        }
        return returnStatement;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      return null;
    }
  }

  private static Editor getEditorForMethod(PsiMethod myMethod, @NotNull Project project, Editor editor, PsiFile psiFile) {

    PsiFile containingFile = myMethod.getContainingFile();
    if (containingFile != psiFile) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, containingFile.getVirtualFile());
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    return editor;
  }

  private static PsiType getHierarchyAdjustedReturnType(PsiMethod method, @NotNull PsiType returnType) {
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

  private @NotNull List<PsiMethod> changeReturnType(PsiMethod method, @NotNull PsiType returnType) {
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
      var processor = JavaRefactoringFactory.getInstance(project).createChangeSignatureProcessor(targetMethod,
                                                                         false, null,
                                                                         myName,
                                                                         returnType,
                                                                         ParameterInfoImpl.fromMethod(targetMethod),
                                                                         null, null, null, null);
      processor.run();
    }

    PsiMethod[] hierarchyMethods = methods;
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      for (PsiMethod psiMethod : hierarchyMethods) {
        OverridingMethodsSearch.search(psiMethod).asIterable().forEach(m -> {
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
    if (!(resolve instanceof PsiClass baseClass)) return true;

    if (returnType instanceof PsiPrimitiveType primitiveType) {
      returnType = primitiveType.getBoxedType(derivedClass);
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
    var handler = CommonJavaRefactoringUtil.getRefactoringSupport().getChangeTypeSignatureHandler();
    handler.runHighlightingTypeMigrationSilently(project, editor, scope, referenceParameterList, type);

    return false;
  }

  private static @Nullable PsiReferenceParameterList findTypeArgumentsList(PsiClass superClass, PsiClass derivedClass) {
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

  private static @Nullable PsiReferenceParameterList extractReferenceParameterList(PsiClass superClass, PsiReferenceList extendsList) {
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

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiType type = myReturnTypePointer.getType();
    if (type == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiMethod method = (PsiMethod)getStartElement();
    type = correctType(method, type);
    PsiFile containingFile = method.getContainingFile();
    if (containingFile == psiFile.getOriginalFile()) {
      PsiMethod methodCopy = PsiTreeUtil.findSameElementInCopy(method, psiFile);
      updateMethodType(methodCopy, type);
      if (!PsiTypes.voidType().equals(type)) {
        ReturnStatementAdder adder = new ReturnStatementAdder(JavaPsiFacade.getElementFactory(project), type);
        adder.addReturnForMethod(psiFile, methodCopy);
      }
      return IntentionPreviewInfo.DIFF;
    }
    PsiModifierList modifiers = method.getModifierList();
    String modifiersText = StreamEx.of(PsiModifier.MODIFIERS).filter(modifiers::hasExplicitModifier).map(mod -> mod + " ").joining();
    PsiType oldType = method.getReturnType();
    String oldTypeText = oldType == null ? "" : oldType.getPresentableText() + " ";
    String newTypeText = type.getPresentableText() + " ";
    String parameters = StreamEx.of(method.getParameterList().getParameters())
      .map(param -> param.getType().getPresentableText() + " " + param.getName())
      .collect(Joining.with(", ").maxChars(50).cutAfterDelimiter());
    String name = method.getName();
    String origText = modifiersText + oldTypeText + name + "(" + parameters + ")";
    String newText = modifiersText + newTypeText + name + "(" + parameters + ")";
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, containingFile.getName(), origText, newText);
  }

  protected void updateMethodType(@NotNull PsiMethod method, @NotNull PsiType type) {
    PsiTypeElement typeElement = method.getReturnTypeElement();
    Project project = method.getProject();
    if (typeElement != null) {
      JavaCodeStyleManager.getInstance(project)
        .shortenClassReferences(typeElement.replace(PsiElementFactory.getInstance(project).createTypeElement(type)));
    } else {
      addReturnType(project, method, type);
    }
  }
}
