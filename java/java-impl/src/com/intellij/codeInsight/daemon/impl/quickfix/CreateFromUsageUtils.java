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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.proc.VariablesProcessor;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author mike
 */
public class CreateFromUsageUtils {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils");
  private static final int MAX_GUESSED_MEMBERS_COUNT = 10;

  public static boolean isValidReference(PsiReference reference, boolean unresolvedOnly) {
    if (!(reference instanceof PsiJavaReference)) return false;
    JavaResolveResult[] results = ((PsiJavaReference)reference).multiResolve(true);
    if(results.length == 0) return false;
    if (!unresolvedOnly) {
      for (JavaResolveResult result : results) {
        if (!result.isValidResult()) return false;
      }
    }
    return true;
  }

  public static boolean isValidMethodReference(PsiReference reference, PsiMethodCallExpression call) {
    if (!(reference instanceof PsiJavaReference)) return false;
    try {
      JavaResolveResult candidate = ((PsiJavaReference) reference).advancedResolve(true);
      PsiElement result = candidate.getElement();
      return result instanceof PsiMethod && PsiUtil.isApplicable((PsiMethod)result, candidate.getSubstitutor(), call.getArgumentList());
    }
    catch (ClassCastException cce) {
      // rear case
      return false;
    }
  }

  public static boolean shouldCreateConstructor(PsiClass targetClass, PsiExpressionList argList, PsiMethod candidate) {
    if (argList == null) return false;
    if (candidate == null) {
      return targetClass != null && !targetClass.isInterface() && !(targetClass instanceof PsiTypeParameter) &&
             !(argList.getExpressions().length == 0 && targetClass.getConstructors().length == 0);
    }
    else {
      return !PsiUtil.isApplicable(candidate, PsiSubstitutor.EMPTY, argList);
    }
  }

  public static void setupMethodBody(PsiMethod method) throws IncorrectOperationException {
    PsiClass aClass = method.getContainingClass();
    setupMethodBody(method, aClass);
  }

  public static void setupMethodBody(final PsiMethod method, final PsiClass aClass) throws IncorrectOperationException {
    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(JavaTemplateUtil.TEMPLATE_FROM_USAGE_METHOD_BODY);
    setupMethodBody(method, aClass, template);
  }

  public static void setupMethodBody(final PsiMethod method, final PsiClass aClass, final FileTemplate template) throws
                                                                                                                 IncorrectOperationException {
    PsiType returnType = method.getReturnType();
    if (returnType == null) {
      returnType = PsiType.VOID;
    }

    JVMElementFactory factory = JVMElementFactories.getFactory(aClass.getLanguage(), aClass.getProject());

    LOG.assertTrue(!aClass.isInterface(), "Interface bodies should be already set up");

    FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
    Properties properties = new Properties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE,
                           PsiTypesUtil.getDefaultValueOfType(returnType));

    JavaTemplateUtil.setClassAndMethodNameProperties(properties, aClass, method);

    @NonNls String methodText;
    CodeStyleManager csManager = CodeStyleManager.getInstance(method.getProject());
    try {
      String bodyText = template.getText(properties);
      if (!bodyText.isEmpty()) bodyText += "\n";
      methodText = returnType.getPresentableText() + " foo () {\n" + bodyText + "}";
      methodText = FileTemplateUtil.indent(methodText, method.getProject(), fileType);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Failed to parse file template",e);
    }

    if (methodText != null) {
      PsiMethod m;
      try {
        m = factory.createMethodFromText(methodText, aClass);
      }
      catch (IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                Messages.showErrorDialog(QuickFixBundle.message("new.method.body.template.error.text"),
                                         QuickFixBundle.message("new.method.body.template.error.title"));
              }
            });
        return;
      }
      PsiCodeBlock oldBody = method.getBody();
      PsiCodeBlock newBody = m.getBody();
      LOG.assertTrue(newBody != null);
      if (oldBody != null) {
        oldBody.replace(newBody);
      } else {
        method.addBefore(newBody, null);
      }
      csManager.reformat(method);
    }
  }

  public static void setupEditor(PsiMethod method, final Editor newEditor) {
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiElement l = PsiTreeUtil.skipSiblingsForward(body.getLBrace(), PsiWhiteSpace.class);
      PsiElement r = PsiTreeUtil.skipSiblingsBackward(body.getRBrace(), PsiWhiteSpace.class);
      if (l != null && r != null) {
        int start = l.getTextRange().getStartOffset();
        int end = r.getTextRange().getEndOffset();
        newEditor.getCaretModel().moveToOffset(Math.max(start, end));
        if (end < start) {
          newEditor.getCaretModel().moveToOffset(end + 1);
          CodeStyleManager styleManager = CodeStyleManager.getInstance(method.getProject());
          PsiFile containingFile = method.getContainingFile();
          final String lineIndent = styleManager.getLineIndent(containingFile, Math.min(start, end));
          PsiDocumentManager manager = PsiDocumentManager.getInstance(method.getProject());
          manager.doPostponedOperationsAndUnblockDocument(manager.getDocument(containingFile));
          EditorModificationUtil.insertStringAtCaret(newEditor, lineIndent);
          EditorModificationUtil.insertStringAtCaret(newEditor, "\n", false, false);
        }
        else {
          //correct position caret for groovy and java methods
          final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);
          info.positionCaret(newEditor, true);
        }
        newEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
  }

  public static void setupMethodParameters(PsiMethod method, TemplateBuilder builder, PsiExpressionList argumentList,
                                           PsiSubstitutor substitutor) throws IncorrectOperationException {
    if (argumentList == null) return;
    PsiExpression[] args = argumentList.getExpressions();

    setupMethodParameters(method, builder, argumentList, substitutor, args);
  }

  public static void setupMethodParameters(final PsiMethod method, final TemplateBuilder builder, final PsiElement contextElement,
                                           final PsiSubstitutor substitutor, final PsiExpression[] arguments) {
    setupMethodParameters(method, builder, contextElement, substitutor, ContainerUtil.map2List(arguments, Pair.<PsiExpression, PsiType>createFunction(null)));
  }

  public static void setupMethodParameters(final PsiMethod method, final TemplateBuilder builder, final PsiElement contextElement,
                                           final PsiSubstitutor substitutor, final List<Pair<PsiExpression, PsiType>> arguments)
    throws IncorrectOperationException {
    PsiManager psiManager = method.getManager();
    JVMElementFactory factory = JVMElementFactories.getFactory(method.getLanguage(), method.getProject());

    PsiParameterList parameterList = method.getParameterList();

    GlobalSearchScope resolveScope = method.getResolveScope();

    GuessTypeParameters guesser = new GuessTypeParameters(JavaPsiFacade.getElementFactory(method.getProject()));

    final PsiClass containingClass = method.getContainingClass();
    final boolean isInterface = containingClass != null && containingClass.isInterface();
    for (int i = 0; i < arguments.size(); i++) {
      Pair<PsiExpression, PsiType> arg = arguments.get(i);
      PsiExpression exp = arg.first;

      PsiType argType = exp == null ? arg.second : exp.getType();
      SuggestedNameInfo suggestedInfo = JavaCodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(
        VariableKind.PARAMETER, null, exp, argType);
      @NonNls String[] names = suggestedInfo.names; //TODO: callback about used name

      if (names.length == 0) {
        names = new String[]{"p" + i};
      }

      if (argType == null || PsiType.NULL.equals(argType) || 
          argType instanceof PsiLambdaExpressionType || 
          argType instanceof PsiLambdaParameterType || 
          argType instanceof PsiMethodReferenceType) {
        argType = PsiType.getJavaLangObject(psiManager, resolveScope);
      } else if (argType instanceof PsiDisjunctionType) {
        argType = ((PsiDisjunctionType)argType).getLeastUpperBound();
      } else if (argType instanceof PsiWildcardType) {
        argType = ((PsiWildcardType)argType).isBounded() ? ((PsiWildcardType)argType).getBound() : PsiType.getJavaLangObject(psiManager, resolveScope);
      }
      PsiParameter parameter;
      if (parameterList.getParametersCount() <= i) {
        parameter = factory.createParameter(names[0], argType);
        if (isInterface) {
          PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, false);
        }
        parameter = (PsiParameter) parameterList.add(parameter);
      } else {
        parameter = parameterList.getParameters()[i];
      }

      ExpectedTypeInfo info = ExpectedTypesProvider.createInfo(argType, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, argType, TailType.NONE);

      PsiElement context = PsiTreeUtil.getParentOfType(contextElement, PsiClass.class, PsiMethod.class);
      guesser.setupTypeElement(parameter.getTypeElement(), new ExpectedTypeInfo[]{info},
                               substitutor, builder, context, containingClass);

      Expression expression = new ParameterNameExpression(names);
      builder.replaceElement(parameter.getNameIdentifier(), expression);
    }
  }

  @Nullable
  public static PsiClass createClass(final PsiJavaCodeReferenceElement referenceElement,
                                     final CreateClassKind classKind,
                                     final String superClassName) {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    final String name = referenceElement.getReferenceName();

    final PsiElement qualifierElement;
    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement) referenceElement.getQualifier();
      qualifierElement = qualifier == null? null : qualifier.resolve();
      if (qualifierElement instanceof PsiClass) {
        return ApplicationManager.getApplication().runWriteAction(
          new Computable<PsiClass>() {
            @Override
            public PsiClass compute() {
              return createClassInQualifier((PsiClass)qualifierElement, classKind, name, referenceElement);
            }
          });
      }
    }
    else {
      qualifierElement = null;
    }

    final PsiManager manager = referenceElement.getManager();
    final PsiFile sourceFile = referenceElement.getContainingFile();
    final Module module = ModuleUtilCore.findModuleForPsiElement(sourceFile);
    PsiPackage aPackage = findTargetPackage(qualifierElement, manager, sourceFile);
    if (aPackage == null) return null;
    final PsiDirectory targetDirectory;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Project project = manager.getProject();
      String title = QuickFixBundle.message("create.class.title", StringUtil.capitalize(classKind.getDescription()));

      CreateClassDialog dialog = new CreateClassDialog(project, title, name, aPackage.getQualifiedName(), classKind, false, module){
        @Override
        protected boolean reportBaseInSourceSelectionInTest() {
          return true;
        }
      };
      dialog.show();
      if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

      targetDirectory = dialog.getTargetDirectory();
      if (targetDirectory == null) return null;
    }
    else {
      targetDirectory = null;
    }
    return createClass(classKind, targetDirectory, name, manager, referenceElement, sourceFile, superClassName);
  }

  @Nullable
  public static PsiPackage findTargetPackage(PsiElement qualifierElement, PsiManager manager, PsiFile sourceFile) {
    PsiPackage aPackage = null;
    if (qualifierElement instanceof PsiPackage) {
      aPackage = (PsiPackage)qualifierElement;
    }
    else {
      final PsiDirectory directory = sourceFile.getContainingDirectory();
      if (directory != null) {
        aPackage = JavaDirectoryService.getInstance().getPackage(directory);
      }

      if (aPackage == null) {
        aPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage("");
      }
    }
    if (aPackage == null) return null;
    return aPackage;
  }

  public static PsiClass createClassInQualifier(PsiClass psiClass,
                                                CreateClassKind classKind,
                                                String name,
                                                PsiJavaCodeReferenceElement referenceElement) {
    try {
      if (!CodeInsightUtilBase.preparePsiElementForWrite(psiClass)) return null;

      PsiManager manager = psiClass.getManager();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      PsiClass result = classKind == CreateClassKind.INTERFACE ? elementFactory.createInterface(name) :
                        classKind == CreateClassKind.CLASS ? elementFactory.createClass(name) :
                        classKind == CreateClassKind.ANNOTATION ? elementFactory.createAnnotationType(name) :
                        elementFactory.createEnum(name);
      CreateFromUsageBaseFix.setupGenericParameters(result, referenceElement);
      result = (PsiClass)CodeStyleManager.getInstance(manager.getProject()).reformat(result);
      return (PsiClass) psiClass.add(result);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public static PsiClass createClass(final CreateClassKind classKind,
                                      final PsiDirectory directory,
                                      final String name,
                                      final PsiManager manager,
                                      final PsiElement contextElement,
                                      final PsiFile sourceFile,
                                      final String superClassName) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiElementFactory factory = facade.getElementFactory();

    return ApplicationManager.getApplication().runWriteAction(
      new Computable<PsiClass>() {
        @Override
        public PsiClass compute() {
          try {
            PsiClass targetClass;
            if (directory != null) {
              try {
                if (classKind == CreateClassKind.INTERFACE) {
                  targetClass = JavaDirectoryService.getInstance().createInterface(directory, name);
                }
                else if (classKind == CreateClassKind.CLASS) {
                  targetClass = JavaDirectoryService.getInstance().createClass(directory, name);
                }
                else if (classKind == CreateClassKind.ENUM) {
                  targetClass = JavaDirectoryService.getInstance().createEnum(directory, name);
                }
                else if (classKind == CreateClassKind.ANNOTATION) {
                  targetClass = JavaDirectoryService.getInstance().createAnnotationType(directory, name);
                }
                else {
                  LOG.error("Unknown kind of a class to create");
                  return null;
                }
              }
              catch (final IncorrectOperationException e) {
                scheduleFileOrPackageCreationFailedMessageBox(e, name, directory, false);
                return null;
              }
              if (!facade.getResolveHelper().isAccessible(targetClass, contextElement, null)) {
                PsiUtil.setModifierProperty(targetClass, PsiModifier.PUBLIC, true);
              }
            }
            else { //tests
              PsiClass aClass;
              if (classKind == CreateClassKind.INTERFACE) {
                aClass = factory.createInterface(name);
              }
              else if (classKind == CreateClassKind.CLASS) {
                aClass = factory.createClass(name);
              }
              else if (classKind == CreateClassKind.ENUM) {
                aClass = factory.createEnum(name);
              }
              else if (classKind == CreateClassKind.ANNOTATION) {
                aClass = factory.createAnnotationType(name);
              }
              else {
                LOG.error("Unknown kind of a class to create");
                return null;
              }
              targetClass = (PsiClass) sourceFile.add(aClass);
            }

            if (superClassName != null) {
              final PsiClass superClass =
                facade.findClass(superClassName, targetClass.getResolveScope());
              final PsiJavaCodeReferenceElement superClassReference = factory.createReferenceElementByFQClassName(superClassName, targetClass.getResolveScope());
              final PsiReferenceList list = classKind == CreateClassKind.INTERFACE || superClass == null || !superClass.isInterface() ?
                targetClass.getExtendsList() : targetClass.getImplementsList();
              list.add(superClassReference);
            }
            if (contextElement instanceof PsiJavaCodeReferenceElement) {
              CreateFromUsageBaseFix.setupGenericParameters(targetClass, (PsiJavaCodeReferenceElement)contextElement);
            }
            return targetClass;
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return null;
          }
        }
      });
  }

  public static void scheduleFileOrPackageCreationFailedMessageBox(final IncorrectOperationException e, final String name, final PsiDirectory directory,
                                                      final boolean isPackage) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(QuickFixBundle.message(
          isPackage ? "cannot.create.java.package.error.text" : "cannot.create.java.file.error.text", name, directory.getVirtualFile().getName(), e.getLocalizedMessage()),
                                 QuickFixBundle.message(
                                   isPackage ? "cannot.create.java.package.error.title" : "cannot.create.java.file.error.title"));
      }
    });
  }

  public static PsiReferenceExpression[] collectExpressions(final PsiExpression expression, Class<? extends PsiElement>... scopes) {
    PsiElement parent = PsiTreeUtil.getParentOfType(expression, scopes);

    final List<PsiReferenceExpression> result = new ArrayList<PsiReferenceExpression>();
    JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expr) {
        if (expression instanceof PsiReferenceExpression) {
          if (expr.textMatches(expression) && !isValidReference(expr, false)) {
            result.add(expr);
          }
        }
        visitElement(expr);
      }

      @Override public void visitMethodCallExpression(PsiMethodCallExpression expr) {
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpression = expr.getMethodExpression();
          if (methodExpression.textMatches(((PsiMethodCallExpression) expression).getMethodExpression())) {
            result.add(expr.getMethodExpression());
          }
        }
        visitElement(expr);
      }
    };

    if (parent != null) {
      parent.accept(visitor);
    }
    return result.toArray(new PsiReferenceExpression[result.size()]);
  }

  public static PsiVariable[] guessMatchingVariables (final PsiExpression expression) {
    List<ExpectedTypeInfo[]> typesList = new ArrayList<ExpectedTypeInfo[]>();
    List<String> expectedMethodNames = new ArrayList<String>();
    List<String> expectedFieldNames  = new ArrayList<String>();

    getExpectedInformation(expression, typesList, expectedMethodNames, expectedFieldNames);

    final List<PsiVariable> list = new ArrayList<PsiVariable>();
    VariablesProcessor varproc = new VariablesProcessor("", true, list){
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        if(!(element instanceof PsiField) ||
           JavaPsiFacade.getInstance(element.getProject()).getResolveHelper().isAccessible((PsiField)element, expression, null)) {
          return super.execute(element, state);
        }
        return true;
      }
    };
    PsiScopesUtil.treeWalkUp(varproc, expression, null);
    PsiVariable[] allVars = varproc.getResultsAsArray();

    ExpectedTypeInfo[] infos = ExpectedTypeUtil.intersect(typesList);

    List<PsiVariable> result = new ArrayList<PsiVariable>();
    nextVar:
    for (PsiVariable variable : allVars) {
      PsiType varType = variable.getType();
      boolean matched = infos.length == 0;
      for (ExpectedTypeInfo info : infos) {
        if (ExpectedTypeUtil.matches(varType, info)) {
          matched = true;
          break;
        }
      }

      if (matched) {
        if (!expectedFieldNames.isEmpty() && !expectedMethodNames.isEmpty()) {
          if (!(varType instanceof PsiClassType)) continue;
          PsiClass aClass = ((PsiClassType)varType).resolve();
          if (aClass == null) continue;
          for (String name : expectedFieldNames) {
            if (aClass.findFieldByName(name, true) == null) continue nextVar;
          }

          for (String name : expectedMethodNames) {
            PsiMethod[] methods = aClass.findMethodsByName(name, true);
            if (methods.length == 0) continue nextVar;
          }
        }

        result.add(variable);
      }
    }

    return result.toArray(new PsiVariable[result.size()]);
  }

  private static void getExpectedInformation(PsiExpression expression,
                                             List<ExpectedTypeInfo[]> types,
                                             List<String> expectedMethodNames,
                                             List<String> expectedFieldNames) {
    for (PsiExpression expr : collectExpressions(expression, PsiMember.class, PsiFile.class)) {
      PsiElement parent = expr.getParent();

      if (!(parent instanceof PsiReferenceExpression)) {
        ExpectedTypeInfo[] someExpectedTypes = ExpectedTypesProvider.getExpectedTypes(expr, false);
        if (someExpectedTypes.length > 0) {
          types.add(someExpectedTypes);
        }
        continue;
      }

      String refName = ((PsiReferenceExpression)parent).getReferenceName();
      if (refName == null) {
        continue;
      }

      PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiMethodCallExpression) {
        expectedMethodNames.add(refName);
        if (refName.equals("equals")) {
          ExpectedTypeInfo[] someExpectedTypes = equalsExpectedTypes((PsiMethodCallExpression)pparent);
          if (someExpectedTypes.length > 0) {
            types.add(someExpectedTypes);
          }
        }
        continue;
      }

      if (pparent instanceof PsiReferenceExpression ||
          pparent instanceof PsiVariable ||
          pparent instanceof PsiExpression) {
        expectedFieldNames.add(refName);
      }
    }
  }

  private static ExpectedTypeInfo[] equalsExpectedTypes(PsiMethodCallExpression methodCall) {
    final PsiType[] argumentTypes = methodCall.getArgumentList().getExpressionTypes();
    if (argumentTypes.length != 1) {
      return ExpectedTypeInfo.EMPTY_ARRAY;
    }
    PsiType type = argumentTypes[0];
    if (type instanceof PsiPrimitiveType) {
      type = ((PsiPrimitiveType)type).getBoxedType(methodCall);
    }
    if (type == null) return ExpectedTypeInfo.EMPTY_ARRAY;
    return new ExpectedTypeInfo[]{ExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.NONE)};
  }

  public static ExpectedTypeInfo[] guessExpectedTypes (PsiExpression expression, boolean allowVoidType) {
        PsiManager manager = expression.getManager();
    GlobalSearchScope resolveScope = expression.getResolveScope();

    List<ExpectedTypeInfo[]> typesList = new ArrayList<ExpectedTypeInfo[]>();
    List<String> expectedMethodNames = new ArrayList<String>();
    List<String> expectedFieldNames  = new ArrayList<String>();

    getExpectedInformation(expression, typesList, expectedMethodNames, expectedFieldNames);


    if (typesList.size() == 1 && (!expectedFieldNames.isEmpty() || !expectedMethodNames.isEmpty())) {
      ExpectedTypeInfo[] infos = typesList.get(0);
      if (infos.length == 1 && infos[0].getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE &&
          infos[0].getType().equals(PsiType.getJavaLangObject(manager, resolveScope))) {
        typesList.clear();
      }
    }

    if (typesList.isEmpty()) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(expression.getProject());
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(expression.getProject());
      PsiElementFactory factory = facade.getElementFactory();
      for (String fieldName : expectedFieldNames) {
        PsiField[] fields = cache.getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (String methodName : expectedMethodNames) {
        PsiMethod[] methods = cache.getMethodsByName(methodName, resolveScope);
        addMemberInfo(methods, expression, typesList, factory);
      }
    }

    ExpectedTypeInfo[] expectedTypes = ExpectedTypeUtil.intersect(typesList);
    if (expectedTypes.length == 0 && !typesList.isEmpty()) {
      List<ExpectedTypeInfo> union = new ArrayList<ExpectedTypeInfo>();
      for (ExpectedTypeInfo[] aTypesList : typesList) {
        ContainerUtil.addAll(union, (ExpectedTypeInfo[])aTypesList);
      }
      expectedTypes = union.toArray(new ExpectedTypeInfo[union.size()]);
    }

    if (expectedTypes.length == 0) {
      PsiType t = allowVoidType ? PsiType.VOID : PsiType.getJavaLangObject(manager, resolveScope);
      expectedTypes = new ExpectedTypeInfo[] {ExpectedTypesProvider.createInfo(t, ExpectedTypeInfo.TYPE_OR_SUBTYPE, t, TailType.NONE)};
    }

    return expectedTypes;
  }


  @Nullable
  public static PsiType[] guessType(PsiExpression expression, final boolean allowVoidType) {
    final PsiManager manager = expression.getManager();
    final GlobalSearchScope resolveScope = expression.getResolveScope();

    List<ExpectedTypeInfo[]> typesList = new ArrayList<ExpectedTypeInfo[]>();
    final List<String> expectedMethodNames = new ArrayList<String>();
    final List<String> expectedFieldNames  = new ArrayList<String>();

    getExpectedInformation(expression, typesList, expectedMethodNames, expectedFieldNames);

    if (typesList.size() == 1 && (!expectedFieldNames.isEmpty() || !expectedMethodNames.isEmpty())) {
      ExpectedTypeInfo[] infos = typesList.get(0);
      if (infos.length == 1 && infos[0].getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE &&
          infos[0].getType().equals(PsiType.getJavaLangObject(manager, resolveScope))) {
        typesList.clear();
      }
    }

    if (typesList.isEmpty()) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(expression.getProject());
      PsiElementFactory factory = facade.getElementFactory();
      for (String fieldName : expectedFieldNames) {
        PsiField[] fields = cache.getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (String methodName : expectedMethodNames) {
        PsiMethod[] methods = cache.getMethodsByName(methodName, resolveScope);
        addMemberInfo(methods, expression, typesList, factory);
      }
    }

    ExpectedTypeInfo[] expectedTypes = ExpectedTypeUtil.intersect(typesList);
    if (expectedTypes.length == 0 && !typesList.isEmpty()) {
      List<ExpectedTypeInfo> union = new ArrayList<ExpectedTypeInfo>();
      for (ExpectedTypeInfo[] aTypesList : typesList) {
        ContainerUtil.addAll(union, (ExpectedTypeInfo[])aTypesList);
      }
      expectedTypes = union.toArray(new ExpectedTypeInfo[union.size()]);
    }

    if (expectedTypes.length == 0) {
      return allowVoidType ? new PsiType[]{PsiType.VOID} : new PsiType[]{PsiType.getJavaLangObject(manager, resolveScope)};
    }
    else {
      //Double check to avoid expensive operations on PsiClassTypes
      final Set<PsiType> typesSet = new HashSet<PsiType>();

      PsiTypeVisitor<PsiType> visitor = new PsiTypeVisitor<PsiType>() {
        @Override
        @Nullable
        public PsiType visitType(PsiType type) {
          if (PsiType.NULL.equals(type)) {
            type = PsiType.getJavaLangObject(manager, resolveScope);
          }
          else if (PsiType.VOID.equals(type) && !allowVoidType) {
            type = PsiType.getJavaLangObject(manager, resolveScope);
          }

          if (!typesSet.contains(type)) {
            if (type instanceof PsiClassType && (!expectedFieldNames.isEmpty() || !expectedMethodNames.isEmpty())) {
              PsiClass aClass = ((PsiClassType) type).resolve();
              if (aClass != null) {
                for (String fieldName : expectedFieldNames) {
                  if (aClass.findFieldByName(fieldName, true) == null) return null;
                }

                for (String methodName : expectedMethodNames) {
                  PsiMethod[] methods = aClass.findMethodsByName(methodName, true);
                  if (methods.length == 0) return null;
                }
              }
            }

            typesSet.add(type);
            return type;
          }

          return null;
        }

        @Override
        public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
          return capturedWildcardType.getUpperBound().accept(this);
        }
      };

      PsiType[] types = ExpectedTypesProvider.processExpectedTypes(expectedTypes, visitor, manager.getProject());
      if (types.length == 0) {
        return allowVoidType
               ? new PsiType[]{PsiType.VOID}
               : new PsiType[]{PsiType.getJavaLangObject(manager, resolveScope)};
      }

      return types;
    }
  }

  private static void addMemberInfo(PsiMember[] members,
                                    final PsiExpression expression,
                                    List<ExpectedTypeInfo[]> types,
                                    PsiElementFactory factory) {
    Arrays.sort(members, new Comparator<PsiMember>() {
      @Override
      public int compare(final PsiMember m1, final PsiMember m2) {
        ProgressManager.checkCanceled();
        int result = JavaStatisticsManager.createInfo(null, m2).getUseCount() - JavaStatisticsManager.createInfo(null, m1).getUseCount();
        if (result != 0) return result;
        final PsiClass aClass = m1.getContainingClass();
        final PsiClass bClass = m2.getContainingClass();
        if (aClass == null || bClass == null) return 0;
        result = JavaStatisticsManager.createInfo(null, bClass).getUseCount() - JavaStatisticsManager.createInfo(null, aClass).getUseCount();
        if (result != 0) return result;

        WeighingComparable<PsiElement,ProximityLocation> proximity1 = PsiProximityComparator.getProximity(m1, expression);
        WeighingComparable<PsiElement,ProximityLocation> proximity2 = PsiProximityComparator.getProximity(m2, expression);
        if (proximity1 != null && proximity2 != null) {
          return proximity2.compareTo(proximity1);
        }

        return 0;
      }
    });

    List<ExpectedTypeInfo> l = new ArrayList<ExpectedTypeInfo>();
    PsiManager manager = expression.getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    for (PsiMember member : members) {
      ProgressManager.checkCanceled();
      PsiClass aClass = member.getContainingClass();
      if (aClass instanceof PsiAnonymousClass || aClass == null) continue;

      if (facade.getResolveHelper().isAccessible(member, expression, null)) {
        PsiClassType type;
        final PsiElement pparent = expression.getParent().getParent();
        if (pparent instanceof PsiMethodCallExpression && member instanceof PsiMethod) {
          PsiSubstitutor substitutor = ExpectedTypeUtil.inferSubstitutor((PsiMethod)member, (PsiMethodCallExpression)pparent, false);
          if (substitutor == null) {
            type = factory.createType(aClass);
          }
          else {
            type = factory.createType(aClass, substitutor);
          }
        }
        else {
          type = factory.createType(aClass);
        }
        l.add(ExpectedTypesProvider.createInfo(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE));
        if (l.size() == MAX_GUESSED_MEMBERS_COUNT) break;
      }
    }

    if (!l.isEmpty()) {
      types.add(l.toArray(new ExpectedTypeInfo[l.size()]));
    }
  }

  public static boolean isAccessedForWriting(final PsiExpression[] expressionOccurences) {
    for (PsiExpression expression : expressionOccurences) {
      if(PsiUtil.isAccessedForWriting(expression)) return true;
    }

    return false;
  }

  public static boolean shouldShowTag(int offset, PsiElement namedElement, PsiElement element) {
    if (namedElement == null) return false;
    TextRange range = namedElement.getTextRange();
    if (range.getLength() == 0) return false;
    boolean isInNamedElement = range.contains(offset);
    return isInNamedElement || element.getTextRange().contains(offset-1);
  }

  public static void addClassesWithMember(final String memberName, final PsiFile file, final Set<String> possibleClassNames, final boolean method,
                                          final boolean staticAccess) {
    addClassesWithMember(memberName, file, possibleClassNames, method, staticAccess, true);
  }

  public static void addClassesWithMember(final String memberName, final PsiFile file, final Set<String> possibleClassNames, final boolean method,
                                          final boolean staticAccess,
                                          final boolean addObjectInheritors) {
    final Project project = file.getProject();
    final Module moduleForFile = ModuleUtilCore.findModuleForPsiElement(file);
    if (moduleForFile == null) return;

    final GlobalSearchScope searchScope = ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      @Override
      public GlobalSearchScope compute() {
        return file.getResolveScope();
      }
    });
    GlobalSearchScope descendantsSearchScope = GlobalSearchScope.moduleWithDependenciesScope(moduleForFile);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

    if (handleObjectMethod(possibleClassNames, facade, searchScope, method, memberName, staticAccess, addObjectInheritors)) {
      return;
    }

    final PsiMember[] members = ApplicationManager.getApplication().runReadAction(new Computable<PsiMember[]>() {
      @Override
      public PsiMember[] compute() {
        return method ? cache.getMethodsByName(memberName, searchScope) : cache.getFieldsByName(memberName, searchScope);
      }
    });

    for (int i = 0; i < members.length; ++i) {
      final PsiMember member = members[i];
      if (hasCorrectModifiers(member, staticAccess)) {
        final PsiClass containingClass = member.getContainingClass();

        if (containingClass != null) {
          final String qName = getQualifiedName(containingClass);
          if (qName == null) continue;

          ClassInheritorsSearch.search(containingClass, descendantsSearchScope, true, true, false).forEach(new Processor<PsiClass>() {
            @Override
            public boolean process(PsiClass psiClass) {
              ContainerUtil.addIfNotNull(getQualifiedName(psiClass), possibleClassNames);
              return true;
            }
          });

          possibleClassNames.add(qName);
        }
      }
      members[i] = null;
    }
  }

  private static boolean handleObjectMethod(Set<String> possibleClassNames, final JavaPsiFacade facade, final GlobalSearchScope searchScope, final boolean method, final String memberName, final boolean staticAccess, boolean addInheritors) {
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(facade.getProject());
    final boolean[] allClasses = {false};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final PsiClass objectClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, searchScope);
        if (objectClass != null) {
          if (method && objectClass.findMethodsByName(memberName, false).length > 0) {
            allClasses[0] = true;
          }
          else if (!method) {
            final PsiField field = objectClass.findFieldByName(memberName, false);
            if (hasCorrectModifiers(field, staticAccess)) {
              allClasses[0] = true;
            }
          }
        }
      }
    });
    if (allClasses[0]) {
      possibleClassNames.add(CommonClassNames.JAVA_LANG_OBJECT);

      if (!addInheritors) {
        return true;
      }

      final String[] strings = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
        @Override
        public String[] compute() {
          return cache.getAllClassNames();
        }
      });
      for (final String className : strings) {
        final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
          @Override
          public PsiClass[] compute() {
            return cache.getClassesByName(className, searchScope);
          }
        });
        for (final PsiClass aClass : classes) {
          final String qname = getQualifiedName(aClass);
          ContainerUtil.addIfNotNull(qname, possibleClassNames);
        }
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static String getQualifiedName(final PsiClass aClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        return aClass.getQualifiedName();
      }
    });
  }

  private static boolean hasCorrectModifiers(@Nullable final PsiMember member, final boolean staticAccess) {
    if (member == null) {
      return false;
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return !member.hasModifierProperty(PsiModifier.PRIVATE) && member.hasModifierProperty(PsiModifier.STATIC) == staticAccess;
      }
    }).booleanValue();
  }

  private static class ParameterNameExpression extends Expression {
    private final String[] myNames;

    public ParameterNameExpression(String[] names) {
      myNames = names;
    }

    @Override
    public Result calculateResult(ExpressionContext context) {
      LookupElement[] lookupItems = calculateLookupItems(context);
      if (lookupItems.length == 0) return new TextResult("");

      return new TextResult(lookupItems[0].getLookupString());
    }

    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    @NotNull
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      Project project = context.getProject();
      int offset = context.getStartOffset();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
      assert file != null;
      PsiElement elementAt = file.findElementAt(offset);
      PsiParameterList parameterList = PsiTreeUtil.getParentOfType(elementAt, PsiParameterList.class);
      if (parameterList == null) {
        if (elementAt == null) return LookupElement.EMPTY_ARRAY;
        final PsiElement parent = elementAt.getParent();
        if (parent instanceof PsiMethod) {
          parameterList = ((PsiMethod)parent).getParameterList();
        }
        else {
          return LookupElement.EMPTY_ARRAY;
        }
      }

      PsiParameter parameter = PsiTreeUtil.getParentOfType(elementAt, PsiParameter.class);

      Set<String> parameterNames = new HashSet<String>();
      for (PsiParameter psiParameter : parameterList.getParameters()) {
        if (psiParameter == parameter) continue;
        parameterNames.add(psiParameter.getName());
      }

      Set<LookupElement> set = new LinkedHashSet<LookupElement>();

      for (String name : myNames) {
        if (parameterNames.contains(name)) {
          int j = 1;
          while (parameterNames.contains(name + j)) j++;
          name += j;
        }

        set.add(LookupElementBuilder.create(name));
      }

      String[] suggestedNames = ExpressionUtil.getNames(context);
      if (suggestedNames != null) {
        for (String name : suggestedNames) {
          if (parameterNames.contains(name)) {
            int j = 1;
            while (parameterNames.contains(name + j)) j++;
            name += j;
          }

          set.add(LookupElementBuilder.create(name));
        }
      }

      return set.toArray(new LookupElement[set.size()]);
    }
  }
}
