// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.magicConstant;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.magicConstant.MagicConstantUtils.AllowedValues;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.java.JavaBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.JdkUtils;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.slicer.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.Joining;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MagicConstantInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Key<Boolean> ANNOTATIONS_BEING_ATTACHED = Key.create("REPORTED_NO_ANNOTATIONS_FOUND");

  private static final CallMapper<AllowedValues> SPECIAL_CASES = new CallMapper<AllowedValues>()
    .register(CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_CALENDAR, "get").parameterTypes("int"),
              MagicConstantInspection::getCalendarGetValues);

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "MagicConstant";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        if (!InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
          Runnable fix = getAttachAnnotationsJarFix(file.getProject());
          if (fix != null) {
            fix.run(); // try to attach automatically
          }
        }
      }

      @Override
      public void visitEnumConstant(PsiEnumConstant enumConstant) {
        checkCall(enumConstant, holder);
      }

      @Override
      public void visitCallExpression(PsiCallExpression callExpression) {
        checkCall(callExpression, holder);
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression r = expression.getRExpression();
        if (r == null) return;
        PsiExpression l = expression.getLExpression();
        if (!(l instanceof PsiReferenceExpression)) return;
        PsiElement resolved = ((PsiReferenceExpression)l).resolve();
        if (!(resolved instanceof PsiModifierListOwner)) return;
        PsiModifierListOwner owner = (PsiModifierListOwner)resolved;
        PsiType type = expression.getType();
        checkExpression(r, owner, type, holder);
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        PsiExpression value = statement.getReturnValue();
        if (value == null) return;
        PsiElement element = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
        PsiMethod method = element instanceof PsiMethod ? (PsiMethod)element : LambdaUtil.getFunctionalInterfaceMethod(element);
        if (method == null) return;
        checkExpression(value, method, value.getType(), holder);
      }

      @Override
      public void visitNameValuePair(PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiExpression)) return;
        PsiReference ref = pair.getReference();
        if (ref == null) return;
        PsiMethod method = (PsiMethod)ref.resolve();
        if (method == null) return;
        checkExpression((PsiExpression)value, method, method.getReturnType(), holder);
      }

      @Override
      public void visitBinaryExpression(PsiBinaryExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQEQ && tokenType != JavaTokenType.NE) return;
        PsiExpression l = expression.getLOperand();
        PsiExpression r = expression.getROperand();
        if (r == null) return;
        checkBinary(l, r);
        checkBinary(r, l);
      }

      @Override
      public void visitCaseLabelElementList(PsiCaseLabelElementList list) {
        PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(list, PsiSwitchBlock.class);
        if (switchBlock == null) return;
        PsiExpression selector = switchBlock.getExpression();
        PsiElement resolved = null;
        if (selector instanceof PsiReference) {
          resolved = ((PsiReference)selector).resolve();
        }
        else if (selector instanceof PsiMethodCallExpression) {
          resolved = ((PsiCallExpression)selector).resolveMethod();
        }
        if (!(resolved instanceof PsiModifierListOwner)) return;
        for (PsiCaseLabelElement element : list.getElements()) {
          if (!(element instanceof PsiExpression)) continue;
          checkExpression((PsiExpression)element, (PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), holder);
        }
      }

      private void checkBinary(@NotNull PsiExpression l, @NotNull PsiExpression r) {
        if (l instanceof PsiReference) {
          PsiElement resolved = ((PsiReference)l).resolve();
          if (resolved instanceof PsiModifierListOwner) {
            checkExpression(r, (PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), holder);
          }
        }
        else if (l instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)l;
          PsiMethod method = call.resolveMethod();
          if (method != null) {
            checkExpression(r, method, method.getReturnType(), holder);
            checkExpression(r, holder, SPECIAL_CASES.mapFirst(call));
          }
        }
      }
    };
  }

  @Override
  public void cleanup(@NotNull Project project) {
    super.cleanup(project);
    project.putUserData(ANNOTATIONS_BEING_ATTACHED, null);
  }

  // returns fix to apply if our own JB "jdkAnnotations" are not attached to the current jdk
  public static Runnable getAttachAnnotationsJarFix(@NotNull Project project) {
    Boolean found = project.getUserData(ANNOTATIONS_BEING_ATTACHED);
    if (found != null) {
      return null;
    }

    Sdk jdk = getJDKToAnnotate(project);
    return jdk == null ? null : () -> attachAnnotationsLaterTo(project, jdk);
  }

  private static Sdk getJDKToAnnotate(@NotNull Project project) {
    PsiClass awtInputEvent = JavaPsiFacade.getInstance(project).findClass("java.awt.event.InputEvent", GlobalSearchScope.allScope(project));
    if (awtInputEvent == null) return null;
    PsiMethod[] methods = awtInputEvent.findMethodsByName("getModifiers", false);
    if (methods.length != 1) return null;
    PsiMethod getModifiers = methods[0];
    Sdk jdk = JdkUtils.getJdkForElement(getModifiers);
    if (jdk == null) return null;
    PsiAnnotation annotation = ExternalAnnotationsManager.getInstance(project).findExternalAnnotation(getModifiers, MagicConstant.class.getName());
    return annotation == null ? jdk : null;
  }

  private static void attachAnnotationsLaterTo(@NotNull Project project, @NotNull Sdk sdk) {
    project.putUserData(ANNOTATIONS_BEING_ATTACHED, Boolean.TRUE);
    ApplicationManager.getApplication().invokeLater(() -> {
      SdkModificator modificator = sdk.getSdkModificator();
      boolean success = JavaSdkImpl.attachIDEAAnnotationsToJdk(modificator);
      // daemon will restart automatically
      if (success) {
        modificator.commitChanges();
      }
      if (success) {
        DumbService.getInstance(project).runWhenSmart(() -> {
          // check if we really attached the necessary annotations, to avoid IDEA-247322
          if (getJDKToAnnotate(project) == null) {
            // avoid endless loop on JDK misconfiguration
            project.putUserData(ANNOTATIONS_BEING_ATTACHED, null);
          }
        });
      }
    }, project.getDisposed());
  }

  private static void checkExpression(@NotNull PsiExpression expression,
                                      @NotNull PsiModifierListOwner owner,
                                      @Nullable PsiType type,
                                      @NotNull ProblemsHolder holder) {
    AllowedValues allowed = MagicConstantUtils.getAllowedValues(owner, type, expression);
    checkExpression(expression, holder, allowed);
  }

  private static void checkExpression(@NotNull PsiExpression expression,
                                      @NotNull ProblemsHolder holder,
                                      AllowedValues allowed) {
    if (allowed == null) return;
    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(expression, null);
    if (scope == null) scope = expression;
    if (!isAllowed(expression, scope, allowed, expression.getManager(), null)) {
      registerProblem(expression, allowed, holder);
    }
  }

  private static void checkCall(@NotNull PsiCall methodCall, @NotNull ProblemsHolder holder) {
    PsiExpressionList argumentList = methodCall.getArgumentList();
    if (argumentList == null) return;
    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiExpression[] arguments = argumentList.getExpressions();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiType type = parameter.getType();
      int stopArg = i;
      if (type instanceof PsiEllipsisType) {
        type = ((PsiEllipsisType)type).getComponentType();
        stopArg = arguments.length - 1;
      }
      AllowedValues values = MagicConstantUtils.getAllowedValues(parameter, type, methodCall);
      if (values == null) continue;
      if (i >= arguments.length) break;
      for (int j = i; j <= stopArg; j++) {
        PsiExpression argument = arguments[j];
        argument = PsiUtil.deparenthesizeExpression(argument);
        if (argument == null) continue;

        checkMagicParameterArgument(parameter, argument, values, holder);
      }
    }
  }

  private static AllowedValues getCalendarGetValues(PsiMethodCallExpression call) {
    Integer argument = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(call.getArgumentList().getExpressions()[0]), Integer.class);
    PsiMethod method = call.resolveMethod();
    if (method == null || argument == null) return null;
    return CachedValuesManager.getCachedValue(method, () -> {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
      Function<String[], AllowedValues> converter = strings -> {
        String expression = StreamEx.of(strings)
                              .map((CommonClassNames.JAVA_UTIL_CALENDAR + ".")::concat).joining(",", "{", "}");
        PsiArrayInitializerExpression initializer = (PsiArrayInitializerExpression)factory.createExpressionFromText(expression, method);
        return new AllowedValues(initializer.getInitializers(), false);
      };
      Map<Integer, AllowedValues> map = new HashMap<>();
      final String[] days = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
      map.put(Calendar.DAY_OF_WEEK, converter.apply(days));
      final String[] months = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
        "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
      map.put(Calendar.MONTH, converter.apply(months));
      final String[] amPm = {"AM", "PM"};
      map.put(Calendar.AM_PM, converter.apply(amPm));
      return CachedValueProvider.Result.create(map, method);
    }).get(argument);
  }

  private static PsiType getType(@NotNull PsiModifierListOwner element) {
    return element instanceof PsiVariable ? ((PsiVariable)element).getType() : element instanceof PsiMethod ? ((PsiMethod)element).getReturnType() : null;
  }

  private static void checkMagicParameterArgument(@NotNull PsiParameter parameter,
                                                  @NotNull PsiExpression argument,
                                                  @NotNull AllowedValues allowedValues,
                                                  @NotNull ProblemsHolder holder) {
    final PsiManager manager = PsiManager.getInstance(holder.getProject());

    if (!argument.getTextRange().isEmpty() && !isAllowed(argument, parameter.getDeclarationScope(), allowedValues, manager, null)) {
      registerProblem(argument, allowedValues, holder);
    }
  }

  private static void registerProblem(@NotNull PsiExpression argument, @NotNull AllowedValues allowedValues, @NotNull ProblemsHolder holder) {
    Function<PsiAnnotationMemberValue, String> formatter = value -> {
      if (value instanceof PsiReferenceExpression) {
        PsiElement resolved = ((PsiReferenceExpression)value).resolve();
        if (resolved instanceof PsiVariable) {
          return PsiFormatUtil.formatVariable((PsiVariable)resolved,
                                              PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS, PsiSubstitutor.EMPTY);
        }
      }
      return value.getText();
    };
    String values = StreamEx.of(allowedValues.getValues()).map(formatter).collect(Joining.with(", ").cutAfterDelimiter().maxCodePoints(100));
    String message = JavaBundle.message("inspection.magic.constants.should.be.one.of.values", values, allowedValues.isFlagSet() ? 1 : 0);
    holder.registerProblem(argument, message, suggestMagicConstant(argument, allowedValues));
  }

  @Nullable // null means no quickfix available
  private static LocalQuickFix suggestMagicConstant(@NotNull PsiExpression argument,
                                                    @NotNull AllowedValues allowedValues) {
    Object argumentValue = JavaConstantExpressionEvaluator.computeConstantExpression(argument, null, false);
    if (argumentValue == null) return null;

    if (!allowedValues.isFlagSet()) {
      for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
        if (value instanceof PsiExpression) {
          Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)value, null, false);
          if (argumentValue.equals(constantValue)) {
            return new ReplaceWithMagicConstantFix(argument, value);
          }
        }
      }
    }
    else {
      Long longArgument = evaluateLongConstant(argument);
      if (longArgument == null) { return null; }

      // try to find ored flags
      long remainingFlags = longArgument.longValue();
      List<PsiAnnotationMemberValue> flags = new ArrayList<>();
      for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
        if (value instanceof PsiExpression) {
          Long constantValue = evaluateLongConstant((PsiExpression)value);
          if (constantValue == null) {
            continue;
          }
          if ((remainingFlags & constantValue) == constantValue) {
            flags.add(value);
            remainingFlags &= ~constantValue;
          }
        }
      }
      if (remainingFlags == 0) {
        // found flags to combine with OR, suggest the fix
        if (flags.size() > 1) {
          for (int i = flags.size() - 1; i >= 0; i--) {
            PsiAnnotationMemberValue flag = flags.get(i);
            Long flagValue = evaluateLongConstant((PsiExpression)flag);
            if (flagValue != null && flagValue == 0) {
              // no sense in ORing with '0'
              flags.remove(i);
            }
          }
        }
        if (!flags.isEmpty()) {
          return new ReplaceWithMagicConstantFix(argument, flags.toArray(PsiAnnotationMemberValue.EMPTY_ARRAY));
        }
      }
    }
    return null;
  }

  private static Long evaluateLongConstant(@NotNull PsiExpression expression) {
    Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, null, false);
    if (constantValue instanceof Long ||
                 constantValue instanceof Integer ||
                 constantValue instanceof Short ||
                 constantValue instanceof Byte) {
      return ((Number)constantValue).longValue();
    }
    return null;
  }

  private static boolean isAllowed(@NotNull final PsiExpression argument, @NotNull final PsiElement scope,
                                   @NotNull final AllowedValues allowedValues,
                                   @NotNull final PsiManager manager,
                                   @Nullable Set<PsiExpression> visited) {
    if (isGoodExpression(argument, allowedValues, scope, manager, visited)) return true;

    return processValuesFlownTo(argument, scope, manager,
                                expression -> isGoodExpression(expression, allowedValues, scope, manager, visited));
  }

  private static boolean isGoodExpression(@NotNull PsiExpression argument,
                                          @NotNull AllowedValues allowedValues,
                                          @NotNull PsiElement scope,
                                          @NotNull PsiManager manager,
                                          @Nullable Set<PsiExpression> visited) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(argument);
    if (expression == null) return true;
    if (visited == null) visited = new HashSet<>();
    if (!visited.add(expression)) return true;
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(thenExpression, scope, allowedValues, manager, visited);
      if (!thenAllowed) return false;
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return elseExpression == null || isAllowed(elseExpression, scope, allowedValues, manager, visited);
    }

    if (isOneOf(expression, allowedValues, manager)) return true;

    if (allowedValues.isFlagSet()) {
      PsiExpression zero = getLiteralExpression(expression, manager, "0");
      if (MagicConstantUtils.same(expression, zero, manager)
          // if for some crazy reason the constant with value "0" is included to allowed values for flags, do not treat literal "0" as allowed value anymore
          // see e.g. Font.BOLD=1, Font.ITALIC=2, Font.PLAIN=0
          && !allowedValues.hasZeroValue()) return true;
      PsiExpression minusOne = getLiteralExpression(expression, manager, "-1");
      if (MagicConstantUtils.same(expression, minusOne, manager)) return true;
      if (expression instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)expression).getOperationTokenType();
        if (JavaTokenType.OR.equals(tokenType) || JavaTokenType.XOR.equals(tokenType) ||
            JavaTokenType.AND.equals(tokenType) || JavaTokenType.PLUS.equals(tokenType)) {
          for (PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
            if (!isAllowed(operand, scope, allowedValues, manager, visited)) return false;
          }
          return true;
        }
      }
      if (expression instanceof PsiPrefixExpression &&
          JavaTokenType.TILDE.equals(((PsiPrefixExpression)expression).getOperationTokenType())) {
        PsiExpression operand = ((PsiPrefixExpression)expression).getOperand();
        return operand == null || isAllowed(operand, scope, allowedValues, manager, visited);
      }
    }

    PsiElement resolved = null;
    AllowedValues allowedForRef = null;
    if (expression instanceof PsiReference) {
      resolved = ((PsiReference)expression).resolve();
    }
    else if (expression instanceof PsiMethodCallExpression) {
      allowedForRef = SPECIAL_CASES.mapFirst((PsiMethodCallExpression)expression);
      resolved = ((PsiCallExpression)expression).resolveMethod();
    }

    if (allowedForRef == null && resolved instanceof PsiModifierListOwner) {
      allowedForRef = MagicConstantUtils.getAllowedValues((PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), expression);
    }
    if (allowedForRef != null && allowedForRef.isSubsetOf(allowedValues, manager)) {
      return true;
    }

    return PsiType.NULL.equals(expression.getType());
  }

  private static final Key<Map<String, PsiExpression>> LITERAL_EXPRESSION_CACHE = Key.create("LITERAL_EXPRESSION_CACHE");
  @NotNull
  private static PsiExpression getLiteralExpression(@NotNull PsiExpression context, @NotNull PsiManager manager, @NotNull String text) {
    Map<String, PsiExpression> cache = LITERAL_EXPRESSION_CACHE.get(manager);
    if (cache == null) {
      cache = ContainerUtil.createConcurrentSoftValueMap();
      cache = manager.putUserDataIfAbsent(LITERAL_EXPRESSION_CACHE, cache);
    }
    PsiExpression expression = cache.get(text);
    if (expression == null) {
      expression = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(text, context);
      cache.put(text, expression);
    }
    return expression;
  }

  private static boolean isOneOf(@NotNull PsiExpression expression, @NotNull AllowedValues allowedValues, @NotNull PsiManager manager) {
    for (PsiAnnotationMemberValue allowedValue : allowedValues.getValues()) {
      if (MagicConstantUtils.same(allowedValue, expression, manager)) return true;
    }
    return false;
  }

  static boolean processValuesFlownTo(@NotNull final PsiExpression argument,
                                      @NotNull PsiElement scope,
                                      @NotNull PsiManager manager,
                                      @NotNull final Processor<? super PsiExpression> processor) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(new LocalSearchScope(scope), manager.getProject());

    SliceRootNode rootNode = new SliceRootNode(manager.getProject(), new DuplicateMap(),
                                               LanguageSlicing.getProvider(argument).createRootUsage(argument, params));

    Collection<? extends AbstractTreeNode<?>> children = ProgressManager.getInstance().runProcess(
      () -> rootNode.getChildren().iterator().next().getChildren(), new ProgressIndicatorBase());
    for (AbstractTreeNode<?> child : children) {
      SliceUsage usage = (SliceUsage)child.getValue();
      PsiElement element = usage != null ? usage.getElement() : null;
      if (element instanceof PsiExpression && !processor.process((PsiExpression)element)) return false;
    }

    return !children.isEmpty();
  }

  private static class ReplaceWithMagicConstantFix extends LocalQuickFixOnPsiElement {
    @SafeFieldForPreview
    private final List<SmartPsiElementPointer<PsiAnnotationMemberValue>> myMemberValuePointers;

    ReplaceWithMagicConstantFix(@NotNull PsiExpression argument, PsiAnnotationMemberValue @NotNull ... values) {
      super(argument);
      myMemberValuePointers =
        ContainerUtil.map(values, SmartPointerManager.getInstance(argument.getProject())::createSmartPsiElementPointer);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.replace.with.magic.constant");
    }

    @NotNull
    @Override
    public String getText() {
      List<String> names = myMemberValuePointers.stream().map(SmartPsiElementPointer::getElement).filter(Objects::nonNull)
                                                .map(PsiElement::getText).collect(Collectors.toList());
      String expression = StringUtil.join(names, " | ");
      return CommonQuickFixBundle.message("fix.replace.with.x", expression);
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      List<PsiAnnotationMemberValue> values = ContainerUtil.map(myMemberValuePointers, SmartPsiElementPointer::getElement);
      String text = StringUtil.join(Collections.nCopies(values.size(), "0"), " | ");
      PsiExpression concatExp = PsiElementFactory.getInstance(project).createExpressionFromText(text, startElement);

      List<PsiLiteralExpression> expressionsToReplace = new ArrayList<>(values.size());
      concatExp.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
          super.visitLiteralExpression(expression);
          if (Integer.valueOf(0).equals(expression.getValue())) {
            expressionsToReplace.add(expression);
          }
        }
      });
      Iterator<PsiAnnotationMemberValue> iterator = values.iterator();
      List<PsiElement> resolved = new ArrayList<>();
      for (PsiLiteralExpression toReplace : expressionsToReplace) {
        PsiAnnotationMemberValue value = iterator.next();
        resolved.add(((PsiReference)value).resolve());
        PsiExpression replaced = (PsiExpression)toReplace.replace(value);
        if (toReplace == concatExp) {
          concatExp = replaced;
        }
      }
      PsiElement newStartElement = startElement.replace(concatExp);
      Iterator<PsiElement> resolvedValuesIterator = resolved.iterator();
      newStartElement.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          PsiElement bound = expression.bindToElement(resolvedValuesIterator.next());
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(bound);
        }
      });
    }

    @Override
    public boolean isAvailable(@NotNull Project project,
                               @NotNull PsiFile file,
                               @NotNull PsiElement startElement,
                               @NotNull PsiElement endElement) {
      boolean allValid = myMemberValuePointers.stream().map(SmartPsiElementPointer::getElement).allMatch(p -> p != null && p.isValid());
      return allValid && super.isAvailable(project, file, startElement, endElement);
    }
  }
}
