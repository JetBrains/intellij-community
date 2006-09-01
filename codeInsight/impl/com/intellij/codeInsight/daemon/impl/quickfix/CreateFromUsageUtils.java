package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.proc.VariablesProcessor;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import static com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind.*;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

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
      if (!(result instanceof PsiMethod)) return false;
      return PsiUtil.isApplicable((PsiMethod) result, candidate.getSubstitutor(),
                                  call.getArgumentList());
    }
    catch (ClassCastException cce) {
      // rear case
      return false;
    }
  }

  public static boolean shouldCreateConstructor(PsiClass targetClass, PsiExpressionList argList, PsiMethod candidate) {
    if (argList == null) return false;
    if (candidate == null) {
      if (targetClass == null || targetClass.isInterface() || targetClass instanceof PsiTypeParameter) return false;
      return !(argList.getExpressions().length == 0 && targetClass.getConstructors().length == 0);
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
    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(FileTemplateManager.TEMPLATE_FROM_USAGE_METHOD_BODY);
    setupMethodBody(method, aClass, template);
  }

  public static void setupMethodBody(final PsiMethod method, final PsiClass aClass, final FileTemplate template) throws
                                                                                                                 IncorrectOperationException {
    PsiType returnType = method.getReturnType();
    if (returnType == null) {
      returnType = PsiType.VOID;
    }

    PsiElementFactory factory = method.getManager().getElementFactory();

    LOG.assertTrue(!aClass.isInterface(), "Interface bodies should be already set up");

    FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(template.getExtension());
    Properties properties = new Properties();
    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.getPresentableText());
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE,
                           PsiTypesUtil.getDefaultValueOfType(returnType));

    FileTemplateUtil.setClassAndMethodNameProperties(properties, aClass, method);

    @NonNls String methodText;
    CodeStyleManager csManager = CodeStyleManager.getInstance(method.getProject());
    try {
      String bodyText = template.getText(properties);
      if (!"".equals(bodyText)) bodyText += "\n";
      methodText = returnType.getPresentableText() + " foo () {\n" + bodyText + "}";
      methodText = FileTemplateUtil.indent(methodText, method.getProject(), fileType);
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
      PsiElement l = PsiTreeUtil.skipSiblingsForward(body.getLBrace(), new Class[] {PsiWhiteSpace.class});
      PsiElement r = PsiTreeUtil.skipSiblingsBackward(body.getRBrace(), new Class[] {PsiWhiteSpace.class});
      if (l != null && r != null) {
        int start = l.getTextRange().getStartOffset(),
            end   = r.getTextRange().getEndOffset();
        newEditor.getCaretModel().moveToOffset(Math.max(start, end));
        newEditor.getSelectionModel().setSelection(Math.min(start, end), Math.max(start, end));
        newEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
  }

  public static void setupMethodParameters(
    PsiMethod method,
    TemplateBuilder builder,
    PsiExpressionList argumentList,
    PsiSubstitutor substitutor) throws IncorrectOperationException {
    if (argumentList == null) return;
    PsiManager psiManager = method.getManager();
    PsiElementFactory factory = psiManager.getElementFactory();

    PsiParameterList parameterList = method.getParameterList();

    PsiExpression[] args = argumentList.getExpressions();
    GlobalSearchScope resolveScope = method.getResolveScope();

    GuessTypeParameters guesser = new GuessTypeParameters(argumentList.getManager().getElementFactory());

    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];

      SuggestedNameInfo suggestedInfo = CodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(
        VariableKind.PARAMETER, null, arg, null);
      @NonNls String[] names = suggestedInfo.names; //TODO: callback about used name

      if (names.length == 0) {
        names = new String[]{"p" + i};
      }

      PsiType argType = arg.getType();
      if (argType == null || argType == PsiType.NULL) {
        argType = PsiType.getJavaLangObject(psiManager, resolveScope);
      }

      PsiParameter parameter = factory.createParameter(names[0], argType);
      parameter = (PsiParameter) parameterList.add(parameter);

      ExpectedTypeInfo info = ExpectedTypesProvider.getInstance(psiManager.getProject()).createInfo(argType,
                                                                                                    ExpectedTypeInfo.TYPE_OR_SUPERTYPE, argType, TailType.NONE);

      PsiElement context = PsiTreeUtil.getParentOfType(argumentList, PsiClass.class, PsiMethod.class);
      guesser.setupTypeElement(parameter.getTypeElement(), new ExpectedTypeInfo[]{info},
                               substitutor, builder, context, method.getContainingClass());

      Expression expression = new ParameterNameExpression(names);
      builder.replaceElement(parameter.getNameIdentifier(), expression);
    }
  }

  public static PsiClass createClass(
    final PsiJavaCodeReferenceElement referenceElement,
    final CreateClassKind classKind,
    final String superClassName) {
    final String name = referenceElement.getReferenceName();

    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement) referenceElement.getQualifier();
      final PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        return ApplicationManager.getApplication().runWriteAction(
          new Computable<PsiClass>() {
            public PsiClass compute() {
              try {
                PsiClass psiClass = (PsiClass) psiElement;
                if (!CodeInsightUtil.preparePsiElementForWrite(psiClass)) return null;

                PsiManager manager = psiClass.getManager();
                PsiElementFactory elementFactory = manager.getElementFactory();
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

    final PsiFile sourceFile = referenceElement.getContainingFile();
    PsiDirectory sourceDir = sourceFile.getContainingDirectory();

    final PsiManager manager = referenceElement.getManager();

    PsiDirectory targetDirectory = null;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Project project = manager.getProject();
      String title = QuickFixBundle.message("create.class.title",
                                            StringUtil.capitalize(classKind.getDescription()));
      PsiPackage aPackage = sourceDir.getPackage();
      CreateClassDialog dialog = new CreateClassDialog(project, title, name,
                                                       aPackage != null ? aPackage.getQualifiedName() : "",
                                                       classKind, false, ModuleUtil.findModuleForPsiElement(sourceFile));
      dialog.show();
      if (dialog.getExitCode() != CreateClassDialog.OK_EXIT_CODE) return null;

      targetDirectory = dialog.getTargetDirectory();
      if (targetDirectory == null) return null;
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
    final PsiElementFactory factory = manager.getElementFactory();

    return ApplicationManager.getApplication().runWriteAction(
      new Computable<PsiClass>() {
        public PsiClass compute() {
          try {
            PsiClass targetClass;
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              try {
                if (classKind == INTERFACE) {
                  targetClass = directory.createInterface(name);
                }
                else if (classKind == CLASS) {
                  targetClass = directory.createClass(name);
                }
                else if (classKind == ENUM) {
                  targetClass = directory.createEnum(name);
                }
                else {
                  LOG.error("Unknown kind of a class to create");
                  return null;
                }
              }
              catch (final IncorrectOperationException e) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                              public void run() {
                                Messages.showErrorDialog(QuickFixBundle.message("cannot.create.java.file.error.text", name,
                                                                                directory.getVirtualFile().getName(),
                                                                                e.getLocalizedMessage()),
                                                         QuickFixBundle.message("cannot.create.java.file.error.title"));
                              }
                            });
                return null;
              }
              if (!manager.getResolveHelper().isAccessible(targetClass, contextElement, null)) {
                targetClass.getModifierList().setModifierProperty(PsiKeyword.PUBLIC, true);
              }
            }
            else {
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
              PsiJavaCodeReferenceElement superClass = factory.createReferenceElementByFQClassName(superClassName, targetClass.getResolveScope());
              targetClass.getExtendsList().add(superClass);
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

  public static PsiReferenceExpression[] collectExpressions(final PsiExpression expression,
                                                            final boolean includeInvalidResolved,
                                                            Class<? extends PsiElement>... scopes
  ) {
    PsiElement parent = PsiTreeUtil.getParentOfType(expression, scopes);

    final List<PsiReferenceExpression> result = new ArrayList<PsiReferenceExpression>();
    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
      public List getResult() {
        return result;
      }

      public void visitReferenceExpression(PsiReferenceExpression expr) {
        if (expression instanceof PsiReferenceExpression) {
          if (expr.textMatches(expression)) {
            result.add(expr);
          }
        }
        visitElement(expr);
      }

      public void visitMethodCallExpression(PsiMethodCallExpression expr) {
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
      public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
        if(!(element instanceof PsiField) ||
           element.getManager().getResolveHelper().isAccessible((PsiField)element, expression, null)) {
          return super.execute(element, substitutor);
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
    PsiExpression[] expressions = collectExpressions(expression, false, PsiMember.class, PsiFile.class);

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

    if (typesList.size() == 1 && (expectedFieldNames.size() > 0 || expectedMethodNames.size() > 0)) {
      ExpectedTypeInfo[] infos = typesList.get(0);
      if (infos.length == 1 && infos[0].getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE &&
          infos[0].getType().equals(PsiType.getJavaLangObject(manager, resolveScope))) {
        typesList.clear();
      }
    }

    if (typesList.size() == 0) {
      PsiElementFactory factory = manager.getElementFactory();
      for (String fieldName : expectedFieldNames) {
        PsiField[] fields = manager.getShortNamesCache().getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (String methodName : expectedMethodNames) {
        PsiMethod[] methods = manager.getShortNamesCache().getMethodsByName(methodName, resolveScope);
        addMemberInfo(methods, expression, typesList, factory);
      }
    }

    ExpectedTypeInfo[] expectedTypes = ExpectedTypeUtil.intersect(typesList);
    if (expectedTypes.length == 0 && !typesList.isEmpty()) {
      List<ExpectedTypeInfo> union = new ArrayList<ExpectedTypeInfo>();
      for (ExpectedTypeInfo[] aTypesList : typesList) {
        union.addAll(Arrays.asList((ExpectedTypeInfo[])aTypesList));
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

    if (typesList.size() == 1 && (expectedFieldNames.size() > 0 || expectedMethodNames.size() > 0)) {
      ExpectedTypeInfo[] infos = typesList.get(0);
      if (infos.length == 1 && infos[0].getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE &&
          infos[0].getType().equals(PsiType.getJavaLangObject(manager, resolveScope))) {
        typesList.clear();
      }
    }

    if (typesList.size() == 0) {
      PsiElementFactory factory = manager.getElementFactory();
      for (String fieldName : expectedFieldNames) {
        PsiField[] fields = manager.getShortNamesCache().getFieldsByName(fieldName, resolveScope);
        addMemberInfo(fields, expression, typesList, factory);
      }

      for (String methodName : expectedMethodNames) {
        PsiMethod[] methods = manager.getShortNamesCache().getMethodsByName(methodName, resolveScope);
        addMemberInfo(methods, expression, typesList, factory);
      }
    }

    ExpectedTypeInfo[] expectedTypes = ExpectedTypeUtil.intersect(typesList);
    if (expectedTypes.length == 0 && !typesList.isEmpty()) {
      List<ExpectedTypeInfo> union = new ArrayList<ExpectedTypeInfo>();
      for (ExpectedTypeInfo[] aTypesList : typesList) {
        union.addAll(Arrays.asList((ExpectedTypeInfo[])aTypesList));
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
            if (type instanceof PsiClassType && (expectedFieldNames.size() > 0 || expectedMethodNames.size() > 0)) {
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
    final StatisticsManager statisticsManager = StatisticsManager.getInstance();
    Arrays.sort(members, new Comparator<PsiMember>() {
      public int compare(final PsiMember m1, final PsiMember m2) {
        int result = statisticsManager.getMemberUseCount(null, m2, null) - statisticsManager.getMemberUseCount(null, m1, null);
        if (result != 0) return result;
        final PsiClass aClass = m1.getContainingClass();
        final PsiClass bClass = m2.getContainingClass();
        if (aClass == null || bClass == null) return 0;
        return statisticsManager.getMemberUseCount(null, bClass, null) - statisticsManager.getMemberUseCount(null, aClass, null);
      }
    });

    List<ExpectedTypeInfo> l = new ArrayList<ExpectedTypeInfo>();
    PsiManager manager = expression.getManager();
    ExpectedTypesProvider provider = ExpectedTypesProvider.getInstance(manager.getProject());
    for (int i = 0; i < Math.min(MAX_GUESSED_MEMBERS_COUNT, members.length); i++) {
      PsiMember member = members[i];
      PsiClass aClass = member.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) continue;

      if (manager.getResolveHelper().isAccessible(aClass, expression, null)) {
        PsiClassType type;
        final PsiElement pparent = expression.getParent().getParent();
        if (pparent instanceof PsiMethodCallExpression) {

          PsiSubstitutor substitutor = ExpectedTypeUtil.inferSubstitutor((PsiMethod)member, (PsiMethodCallExpression)pparent, false);
          if (substitutor == null) {
            type = factory.createType(aClass);
          } else {
            type = factory.createType(aClass, substitutor);
          }
        }
        else {
          type = factory.createType(aClass);
        }
        l.add(provider.createInfo(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE));
      }
    }

    if (l.size() > 0) {
      types.add(l.toArray(new ExpectedTypeInfo[l.size()]));
    }
  }

  public static boolean isAccessedForWriting(final PsiExpression[] expressionOccurences) {
    for (PsiExpression expression : expressionOccurences) {
      if(PsiUtil.isAccessedForWriting(expression)) return true;
    }

    return false;
  }

  private static class ParameterNameExpression implements Expression {
    private final String[] myNames;

    public ParameterNameExpression(String[] names) {
      myNames = names;
    }

    public Result calculateResult(ExpressionContext context) {
      LookupItem[] lookupItems = calculateLookupItems(context);
      if (lookupItems.length == 0) return new TextResult("");

      return new TextResult(lookupItems[0].getLookupString());
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    public LookupItem[] calculateLookupItems(ExpressionContext context) {
      Project project = context.getProject();
      int offset = context.getStartOffset();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
      PsiElement elementAt = file.findElementAt(offset);
      PsiParameterList parameterList = PsiTreeUtil.getParentOfType(elementAt, PsiParameterList.class);
      if (parameterList == null) return new LookupItem[0];

      PsiParameter parameter = PsiTreeUtil.getParentOfType(elementAt, PsiParameter.class);

      Set<String> parameterNames = new HashSet<String>();
      PsiParameter[] parameters = parameterList.getParameters();
      for (PsiParameter psiParameter : parameters) {
        if (psiParameter == parameter) continue;
        parameterNames.add(psiParameter.getName());
      }

      HashSet<String> names = new HashSet<String>();
      Set<LookupItem> set = new LinkedHashSet<LookupItem>();

      for (String name : myNames) {
        if (parameterNames.contains(name)) {
          int j = 1;
          while (parameterNames.contains(name + j)) j++;
          name += j;
        }

        names.add(name);
        LookupItemUtil.addLookupItem(set, name, "");
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
            LookupItemUtil.addLookupItem(set, name, "");
          }
        }
      }

      return set.toArray(new LookupItem[set.size()]);
    }
  }
}
