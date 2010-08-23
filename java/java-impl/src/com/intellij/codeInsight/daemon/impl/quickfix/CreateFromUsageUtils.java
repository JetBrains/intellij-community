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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.proc.VariablesProcessor;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind.*;

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

    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();

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
      if (!"".equals(bodyText)) bodyText += "\n";
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

  public static void setupEditor(PsiMethod method, Editor newEditor) {
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiElement l = PsiTreeUtil.skipSiblingsForward(body.getLBrace(), PsiWhiteSpace.class);
      PsiElement r = PsiTreeUtil.skipSiblingsBackward(body.getRBrace(), PsiWhiteSpace.class);
      if (l != null && r != null) {
        int start = l.getTextRange().getStartOffset(),
            end   = r.getTextRange().getEndOffset();
        newEditor.getCaretModel().moveToOffset(Math.max(start, end));
        newEditor.getSelectionModel().setSelection(Math.min(start, end), Math.max(start, end));
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
    setupMethodParameters(method, builder, contextElement, substitutor, ContainerUtil.map2List(arguments, Pair.createFunction(PsiExpression.class, ((PsiType)null))));
  }

  public static void setupMethodParameters(final PsiMethod method, final TemplateBuilder builder, final PsiElement contextElement,
                                           final PsiSubstitutor substitutor, final List<Pair<PsiExpression, PsiType>> arguments)
    throws IncorrectOperationException {
    PsiManager psiManager = method.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();

    PsiParameterList parameterList = method.getParameterList();

    GlobalSearchScope resolveScope = method.getResolveScope();

    GuessTypeParameters guesser = new GuessTypeParameters(factory);

    final boolean isInterface = method.getContainingClass().isInterface();
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

      if (argType == null || PsiType.NULL.equals(argType)) {
        argType = PsiType.getJavaLangObject(psiManager, resolveScope);
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
                               substitutor, builder, context, method.getContainingClass());

      Expression expression = new ParameterNameExpression(names);
      builder.replaceElement(parameter.getNameIdentifier(), expression);
    }
  }

  @Nullable
  public static PsiClass createClass(final PsiJavaCodeReferenceElement referenceElement,
                                     final CreateClassKind classKind,
                                     final String superClassName) {
    final String name = referenceElement.getReferenceName();

    final PsiElement qualifierElement;
    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement) referenceElement.getQualifier();
      qualifierElement = qualifier == null? null : qualifier.resolve();
      if (qualifierElement instanceof PsiClass) {
        return ApplicationManager.getApplication().runWriteAction(
          new Computable<PsiClass>() {
            public PsiClass compute() {
              try {
                PsiClass psiClass = (PsiClass) qualifierElement;
                if (!CodeInsightUtilBase.preparePsiElementForWrite(psiClass)) return null;

                PsiManager manager = psiClass.getManager();
                PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
                PsiClass result = classKind == INTERFACE ? elementFactory.createInterface(name) :
                                  classKind == CLASS ? elementFactory.createClass(name) :
                                  elementFactory.createEnum(name);
                result = (PsiClass)manager.getCodeStyleManager().reformat(result);
                return (PsiClass) psiClass.add(result);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
                return null;
              }
            }
          });
      }
    }
    else {
      qualifierElement = null;
    }

    final PsiManager manager = referenceElement.getManager();
    final PsiFile sourceFile = referenceElement.getContainingFile();
    final Module module = ModuleUtil.findModuleForPsiElement(sourceFile);
    PsiPackage aPackage = null;
    if (qualifierElement instanceof PsiPackage) {
      aPackage = ((PsiPackage)qualifierElement);
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
    final PsiDirectory targetDirectory;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Project project = manager.getProject();
      String title = QuickFixBundle.message("create.class.title", StringUtil.capitalize(classKind.getDescription()));

      CreateClassDialog dialog = new CreateClassDialog(project, title, name, aPackage.getQualifiedName(), classKind, false, module);
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
        public PsiClass compute() {
          try {
            PsiClass targetClass;
            if (directory != null) {
              try {
                if (classKind == INTERFACE) {
                  targetClass = JavaDirectoryService.getInstance().createInterface(directory, name);
                }
                else if (classKind == CLASS) {
                  targetClass = JavaDirectoryService.getInstance().createClass(directory, name);
                }
                else if (classKind == ENUM) {
                  targetClass = JavaDirectoryService.getInstance().createEnum(directory, name);
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
                PsiUtil.setModifierProperty(targetClass, PsiKeyword.PUBLIC, true);
              }
            }
            else { //tests
              PsiClass aClass;
              if (classKind == INTERFACE) {
                aClass = factory.createInterface(name);
              }
              else if (classKind == CLASS) {
                aClass = factory.createClass(name);
              }
              else if (classKind == ENUM) {
                aClass = factory.createEnum(name);
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
              final PsiReferenceList list = classKind == INTERFACE || superClass == null || !superClass.isInterface() ?
                targetClass.getExtendsList() : targetClass.getImplementsList();
              list.add(superClassReference);
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
      public List getResult() {
        return result;
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expr) {
        if (expression instanceof PsiReferenceExpression) {
          if (expr.textMatches(expression)) {
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

    parent.accept(visitor);
    return result.toArray(new PsiReferenceExpression[result.size()]);
  }

  public static PsiVariable[] guessMatchingVariables (final PsiExpression expression) {
    List<ExpectedTypeInfo[]> typesList = new ArrayList<ExpectedTypeInfo[]>();
    List<String> expectedMethodNames = new ArrayList<String>();
    List<String> expectedFieldNames  = new ArrayList<String>();

    getExpectedInformation(expression, typesList, expectedMethodNames, expectedFieldNames);

    final List<PsiVariable> list = new ArrayList<PsiVariable>();
    VariablesProcessor varproc = new VariablesProcessor("", true, list){
      public boolean execute(PsiElement element, ResolveState state) {
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

  private static void getExpectedInformation (PsiExpression expression, List<ExpectedTypeInfo[]> types,
                                              List<String> expectedMethodNames,
                                              List<String> expectedFieldNames) {
    PsiExpression[] expressions = collectExpressions(expression, PsiMember.class, PsiFile.class);

    for (PsiExpression expr : expressions) {
      PsiElement parent = expr.getParent();
      if (parent instanceof PsiReferenceExpression) {
        PsiElement pparent = parent.getParent();
        if (pparent instanceof PsiMethodCallExpression) {
          String refName = ((PsiReferenceExpression)parent).getReferenceName();
          if (refName != null) {
            expectedMethodNames.add(refName);
          }
        }
        else if (pparent instanceof PsiReferenceExpression || pparent instanceof PsiVariable ||
                 pparent instanceof PsiExpression) {
          String refName = ((PsiReferenceExpression)parent).getReferenceName();
          if (refName != null) {
            expectedFieldNames.add(refName);
          }
        }
      }
      else {
        ExpectedTypeInfo[] someExpectedTypes = ExpectedTypesProvider.getInstance(expression.getProject()).getExpectedTypes(expr, false);
        if (someExpectedTypes.length > 0) {
          types.add(someExpectedTypes);
        }
      }
    }
  }

  public static ExpectedTypeInfo[] guessExpectedTypes (PsiExpression expression, boolean allowVoidType) {
        PsiManager manager = expression.getManager();
    GlobalSearchScope resolveScope = expression.getResolveScope();

    ExpectedTypesProvider provider = ExpectedTypesProvider.getInstance(manager.getProject());
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
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      PsiElementFactory factory = facade.getElementFactory();
      for (String fieldName : expectedFieldNames) {
        PsiField[] fields = facade.getShortNamesCache().getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (String methodName : expectedMethodNames) {
        PsiMethod[] methods = facade.getShortNamesCache().getMethodsByName(methodName, resolveScope);
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

    if (expectedTypes == null || expectedTypes.length == 0) {
      PsiType t = allowVoidType ? PsiType.VOID : PsiType.getJavaLangObject(manager, resolveScope);
      expectedTypes = new ExpectedTypeInfo[] {provider.createInfo(t, ExpectedTypeInfo.TYPE_OR_SUBTYPE, t, TailType.NONE)};
    }

    return expectedTypes;
  }


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
      PsiElementFactory factory = facade.getElementFactory();
      for (String fieldName : expectedFieldNames) {
        PsiField[] fields = facade.getShortNamesCache().getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (String methodName : expectedMethodNames) {
        PsiMethod[] methods = facade.getShortNamesCache().getMethodsByName(methodName, resolveScope);
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

    if (expectedTypes == null || expectedTypes.length == 0) {
      return allowVoidType ? new PsiType[]{PsiType.VOID} : new PsiType[]{PsiType.getJavaLangObject(manager, resolveScope)};
    }
    else {
      //Double check to avoid expensive operations on PsiClassTypes
      final Set<PsiType> typesSet = new HashSet<PsiType>();

      PsiTypeVisitor<PsiType> visitor = new PsiTypeVisitor<PsiType>() {
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

        public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
          return capturedWildcardType.getUpperBound().accept(this);
        }
      };

      ExpectedTypesProvider provider = ExpectedTypesProvider.getInstance(manager.getProject());
      PsiType[] types = provider.processExpectedTypes(expectedTypes, visitor, manager.getProject());
      if (types.length == 0) {
        return allowVoidType
               ? new PsiType[]{PsiType.VOID}
               : new PsiType[]{PsiType.getJavaLangObject(manager, resolveScope)};
      }

      return types;
    }
  }

  private static void addMemberInfo(PsiMember[] members,
                                    PsiExpression expression,
                                    List<ExpectedTypeInfo[]> types,
                                    PsiElementFactory factory) {
    Arrays.sort(members, new Comparator<PsiMember>() {
      public int compare(final PsiMember m1, final PsiMember m2) {
        int result = JavaStatisticsManager.createInfo(null, m2).getUseCount() - JavaStatisticsManager.createInfo(null, m1).getUseCount();
        if (result != 0) return result;
        final PsiClass aClass = m1.getContainingClass();
        final PsiClass bClass = m2.getContainingClass();
        if (aClass == null || bClass == null) return 0;
        return JavaStatisticsManager.createInfo(null, bClass).getUseCount() - JavaStatisticsManager.createInfo(null, aClass).getUseCount();
      }
    });

    List<ExpectedTypeInfo> l = new ArrayList<ExpectedTypeInfo>();
    PsiManager manager = expression.getManager();
    ExpectedTypesProvider provider = ExpectedTypesProvider.getInstance(manager.getProject());
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    for (int i = 0; i < Math.min(MAX_GUESSED_MEMBERS_COUNT, members.length); i++) {
      ProgressManager.checkCanceled();
      PsiMember member = members[i];
      PsiClass aClass = member.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) continue;

      if (facade.getResolveHelper().isAccessible(aClass, expression, null)) {
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
        l.add(provider.createInfo(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE));
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
    final Module moduleForFile = ModuleUtil.findModuleForPsiElement(file);
    if (moduleForFile == null) return;

    final GlobalSearchScope searchScope = file.getResolveScope();
    GlobalSearchScope descendantsSearchScope = GlobalSearchScope.moduleWithDependenciesScope(moduleForFile);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiShortNamesCache cache = facade.getShortNamesCache();

    if (handleObjectMethod(possibleClassNames, facade, searchScope, method, memberName, staticAccess, addObjectInheritors)) {
      return;
    }

    final PsiMember[] members = ApplicationManager.getApplication().runReadAction(new Computable<PsiMember[]>() {
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
    final PsiShortNamesCache cache = facade.getShortNamesCache();
    final boolean[] allClasses = {false};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
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
        public String[] compute() {
          return cache.getAllClassNames();
        }
      });
      for (final String className : strings) {
        final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
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

    public Result calculateResult(ExpressionContext context) {
      LookupElement[] lookupItems = calculateLookupItems(context);
      if (lookupItems.length == 0) return new TextResult("");

      return new TextResult(lookupItems[0].getLookupString());
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      Project project = context.getProject();
      int offset = context.getStartOffset();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
      PsiElement elementAt = file.findElementAt(offset);
      PsiParameterList parameterList = PsiTreeUtil.getParentOfType(elementAt, PsiParameterList.class);
      if (parameterList == null) return LookupElement.EMPTY_ARRAY;

      PsiParameter parameter = PsiTreeUtil.getParentOfType(elementAt, PsiParameter.class);

      Set<String> parameterNames = new HashSet<String>();
      PsiParameter[] parameters = parameterList.getParameters();
      for (PsiParameter psiParameter : parameters) {
        if (psiParameter == parameter) continue;
        parameterNames.add(psiParameter.getName());
      }

      HashSet<String> names = new HashSet<String>();
      Set<LookupElement> set = new LinkedHashSet<LookupElement>();

      for (String name : myNames) {
        if (parameterNames.contains(name)) {
          int j = 1;
          while (parameterNames.contains(name + j)) j++;
          name += j;
        }

        names.add(name);
        LookupItemUtil.addLookupItem(set, name);
      }

      String[] suggestedNames = ExpressionUtil.getNames(context);
      if (suggestedNames != null) {
        for (String name : suggestedNames) {
          if (parameterNames.contains(name)) {
            int j = 1;
            while (parameterNames.contains(name + j)) j++;
            name += j;
          }

          if (!names.contains(name)) {
            LookupItemUtil.addLookupItem(set, name);
          }
        }
      }

      return set.toArray(new LookupElement[set.size()]);
    }
  }
}
