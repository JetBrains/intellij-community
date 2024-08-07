// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.magicConstant;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.magicConstant.MagicConstantUtils.AllowedValues;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.java.JavaBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.JdkUtils;
import com.intellij.openapi.util.Computable;
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
import com.intellij.util.concurrency.AppExecutorUtil;
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

public final class MagicConstantInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Key<Boolean> ANNOTATIONS_BEING_ATTACHED = Key.create("REPORTED_NO_ANNOTATIONS_FOUND");

  private static final CallMapper<AllowedValues> SPECIAL_CASES = new CallMapper<AllowedValues>()
    .register(CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_CALENDAR, "get").parameterTypes("int"),
              MagicConstantInspection::getCalendarGetValues);

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  public @NotNull String getShortName() {
    return "MagicConstant";
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(@NotNull PsiJavaFile file) {
        if (!InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
          Runnable fix = getAttachAnnotationsJarFix(file.getProject());
          if (fix != null) {
            fix.run(); // try to attach automatically
          }
        }
      }

      @Override
      public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
        checkCall(enumConstant, holder);
      }

      @Override
      public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
        checkCall(callExpression, holder);
      }

      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        PsiExpression r = expression.getRExpression();
        if (r != null &&
            expression.getLExpression() instanceof PsiReferenceExpression ref &&
            ref.resolve() instanceof PsiModifierListOwner owner) {
          PsiType type = expression.getType();
          checkExpression(r, owner, type, holder);
        }
      }

      @Override
      public void visitVariable(@NotNull PsiVariable variable) {
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
            checkExpression(initializer, variable, variable.getType(), holder);
        }
      }

      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        PsiExpression value = statement.getReturnValue();
        if (value == null) return;
        PsiElement element = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
        PsiMethod method = element instanceof PsiMethod ? (PsiMethod)element : LambdaUtil.getFunctionalInterfaceMethod(element);
        if (method == null) return;
        checkExpression(value, method, value.getType(), holder);
      }

      @Override
      public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiExpression expression)) return;
        PsiReference ref = pair.getReference();
        if (ref == null) return;
        PsiMethod method = (PsiMethod)ref.resolve();
        if (method == null) return;
        checkExpression(expression, method, method.getReturnType(), holder);
      }

      @Override
      public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQEQ && tokenType != JavaTokenType.NE) return;
        PsiExpression l = expression.getLOperand();
        PsiExpression r = expression.getROperand();
        if (r == null) return;
        checkBinary(l, r);
        checkBinary(r, l);
      }

      @Override
      public void visitCaseLabelElementList(@NotNull PsiCaseLabelElementList list) {
        PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(list, PsiSwitchBlock.class);
        if (switchBlock == null) return;
        PsiExpression selector = switchBlock.getExpression();
        PsiModifierListOwner owner = null;
        if (selector instanceof PsiReference ref) {
          owner = ObjectUtils.tryCast(ref.resolve(), PsiModifierListOwner.class);
        }
        else if (selector instanceof PsiMethodCallExpression call) {
          owner = call.resolveMethod();
        }
        if (owner == null) return;
        for (PsiCaseLabelElement element : list.getElements()) {
          if (element instanceof PsiExpression expression) {
            checkExpression(expression, owner, PsiUtil.getTypeByPsiElement(owner), holder);
          }
        }
      }

      private void checkBinary(@NotNull PsiExpression l, @NotNull PsiExpression r) {
        if (l instanceof PsiReference ref && ref.resolve() instanceof PsiModifierListOwner owner) {
          checkExpression(r, owner, PsiUtil.getTypeByPsiElement(owner), holder);
        }
        else if (l instanceof PsiMethodCallExpression call) {
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
      JavaSdkImpl.attachIDEAAnnotationsToJdkAsync(sdk)
        .onSuccess(success -> {
          // daemon will restart automatically
          if (success) {
            ReadAction.nonBlocking(() -> {
                // check if we really attached the necessary annotations, to avoid IDEA-247322
                return getJDKToAnnotate(project) == null;
              }).finishOnUiThread(ModalityState.nonModal(), hasNoJdkToAnnotate -> {
                if (hasNoJdkToAnnotate) {
                  // avoid endless loop on JDK misconfiguration
                  project.putUserData(ANNOTATIONS_BEING_ATTACHED, null);
                }
              }).inSmartMode(project)
              .submit(AppExecutorUtil.getAppExecutorService());
          }
        });
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
    if (!isAllowed(expression, scope, allowed, expression.getManager(), null, holder.isOnTheFly())) {
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
      if (type instanceof PsiEllipsisType ellipsisType) {
        type = ellipsisType.getComponentType();
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

  private static void checkMagicParameterArgument(@NotNull PsiParameter parameter,
                                                  @NotNull PsiExpression argument,
                                                  @NotNull AllowedValues allowedValues,
                                                  @NotNull ProblemsHolder holder) {
    final PsiManager manager = PsiManager.getInstance(holder.getProject());

    if (!argument.getTextRange().isEmpty() &&
        !isAllowed(argument, parameter.getDeclarationScope(), allowedValues, manager, null, holder.isOnTheFly())) {
      registerProblem(argument, allowedValues, holder);
    }
  }

  private static void registerProblem(@NotNull PsiExpression argument, @NotNull AllowedValues allowedValues, @NotNull ProblemsHolder holder) {
    Function<PsiAnnotationMemberValue, String> formatter = value -> {
      if (value instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiVariable variable) {
          return PsiFormatUtil.formatVariable(variable,
                                              PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS, PsiSubstitutor.EMPTY);
      }
      return value.getText();
    };
    String values = StreamEx.of(allowedValues.getValues()).map(formatter).collect(Joining.with(", ").cutAfterDelimiter().maxCodePoints(100));
    String message = JavaBundle.message("inspection.magic.constants.should.be.one.of.values", values, allowedValues.isFlagSet() ? 1 : 0);
    holder.registerProblem(argument, message, LocalQuickFix.notNullElements(suggestMagicConstant(argument, allowedValues)));
  }

  // null means no quickfix available
  private static @Nullable LocalQuickFix suggestMagicConstant(@NotNull PsiExpression argument,
                                                    @NotNull AllowedValues allowedValues) {
    Object argumentValue = JavaConstantExpressionEvaluator.computeConstantExpression(argument, null, false);
    if (argumentValue == null) return null;

    if (!allowedValues.isFlagSet()) {
      for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
        if (value instanceof PsiExpression expression) {
          Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, null, false);
          if (argumentValue.equals(constantValue)) {
            return LocalQuickFix.from(new ReplaceWithMagicConstantFix(argument, value));
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
        if (value instanceof PsiExpression expression) {
          Long constantValue = evaluateLongConstant(expression);
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
          return LocalQuickFix.from(new ReplaceWithMagicConstantFix(argument, flags.toArray(PsiAnnotationMemberValue.EMPTY_ARRAY)));
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

  private static boolean isAllowed(final @NotNull PsiExpression argument,
                                   final @NotNull PsiElement scope,
                                   final @NotNull AllowedValues allowedValues,
                                   final @NotNull PsiManager manager,
                                   @Nullable Set<PsiExpression> visited,
                                   boolean isOnTheFly) {
    if (isGoodExpression(argument, allowedValues, scope, manager, visited, isOnTheFly)) return true;

    return processValuesFlownTo(argument, scope, manager,
                                isOnTheFly, expression -> isGoodExpression(expression, allowedValues, scope, manager, visited, isOnTheFly)
    );
  }

  private static boolean isGoodExpression(@NotNull PsiExpression argument,
                                          @NotNull AllowedValues allowedValues,
                                          @NotNull PsiElement scope,
                                          @NotNull PsiManager manager,
                                          @Nullable Set<PsiExpression> visited,
                                          boolean isOnTheFly) {
    if (visited == null) visited = new HashSet<>();
    if (!visited.add(argument)) return false;
    if (argument instanceof PsiParenthesizedExpression ||
        argument instanceof PsiConditionalExpression ||
        argument instanceof PsiSwitchExpression) {
      List<PsiExpression> expressions = ExpressionUtils.nonStructuralChildren(argument).toList();
      for (PsiExpression expression : expressions) {
        if (!isAllowed(expression, scope, allowedValues, manager, visited, isOnTheFly)) {
          return false;
        }
      }
      return true;
    }

    if (isOneOf(argument, allowedValues, manager)) return true;

    if (allowedValues.isFlagSet()) {
      PsiExpression zero = getLiteralExpression(argument, manager, "0");
      if (MagicConstantUtils.same(argument, zero, manager)
          // if for some crazy reason the constant with value "0" is included to allowed values for flags, do not treat literal "0" as allowed value anymore
          // see e.g. Font.BOLD=1, Font.ITALIC=2, Font.PLAIN=0
          && !allowedValues.hasZeroValue()) return true;
      PsiExpression minusOne = getLiteralExpression(argument, manager, "-1");
      if (MagicConstantUtils.same(argument, minusOne, manager)) return true;
      if (argument instanceof PsiPolyadicExpression polyadic) {
        IElementType tokenType = polyadic.getOperationTokenType();
        if (JavaTokenType.OR.equals(tokenType) || JavaTokenType.XOR.equals(tokenType) ||
            JavaTokenType.AND.equals(tokenType) || JavaTokenType.PLUS.equals(tokenType)) {
          for (PsiExpression operand : polyadic.getOperands()) {
            if (!isAllowed(operand, scope, allowedValues, manager, visited, isOnTheFly)) return false;
          }
          return true;
        }
      }
      if (argument instanceof PsiPrefixExpression prefixExpression &&
          JavaTokenType.TILDE.equals(prefixExpression.getOperationTokenType())) {
        PsiExpression operand = prefixExpression.getOperand();
        return operand == null || isAllowed(operand, scope, allowedValues, manager, visited, isOnTheFly);
      }
    }

    PsiModifierListOwner owner = null;
    AllowedValues allowedForRef = null;
    if (argument instanceof PsiReference reference) {
      owner = ObjectUtils.tryCast(reference.resolve(), PsiModifierListOwner.class);
    }
    else if (argument instanceof PsiMethodCallExpression call) {
      allowedForRef = SPECIAL_CASES.mapFirst(call);
      owner = call.resolveMethod();
    }

    if (allowedForRef == null && owner != null) {
      allowedForRef = MagicConstantUtils.getAllowedValues(owner, PsiUtil.getTypeByPsiElement(owner), argument);
    }
    if (allowedForRef != null && allowedForRef.isSubsetOf(allowedValues, manager)) {
      return true;
    }

    return PsiTypes.nullType().equals(argument.getType());
  }

  private static final Key<Map<String, PsiExpression>> LITERAL_EXPRESSION_CACHE = Key.create("LITERAL_EXPRESSION_CACHE");
  private static @NotNull PsiExpression getLiteralExpression(@NotNull PsiExpression context, @NotNull PsiManager manager, @NotNull String text) {
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
    return ContainerUtil.exists(allowedValues.getValues(), e -> MagicConstantUtils.same(e, expression, manager));
  }

  static boolean processValuesFlownTo(final @NotNull PsiExpression argument,
                                      @NotNull PsiElement scope,
                                      @NotNull PsiManager manager,
                                      boolean isOnTheFly,
                                      final @NotNull Processor<? super PsiExpression> processor) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(new LocalSearchScope(scope), manager.getProject());

    SliceRootNode rootNode = new SliceRootNode(manager.getProject(), new DuplicateMap(),
                                               LanguageSlicing.getProvider(argument).createRootUsage(argument, params));

    Computable<Collection<SliceNode>> computable = () -> rootNode.getChildren().iterator().next().getChildren();
    Collection<? extends AbstractTreeNode<?>> children;
    if (isOnTheFly) {
      children = computable.compute();
    }
    else {
      children = ProgressManager.getInstance().runProcess(
        computable, new ProgressIndicatorBase());
    }
    for (AbstractTreeNode<?> child : children) {
      SliceUsage usage = (SliceUsage)child.getValue();
      PsiElement element = usage != null ? usage.getElement() : null;
      if (element instanceof PsiExpression expression && !processor.process(expression)) return false;
    }

    return !children.isEmpty();
  }

  private static class ReplaceWithMagicConstantFix extends PsiUpdateModCommandAction<PsiExpression> {
    private final List<SmartPsiElementPointer<PsiAnnotationMemberValue>> myMemberValuePointers;

    ReplaceWithMagicConstantFix(@NotNull PsiExpression argument, PsiAnnotationMemberValue @NotNull ... values) {
      super(argument);
      myMemberValuePointers =
        ContainerUtil.map(values, SmartPointerManager.getInstance(argument.getProject())::createSmartPsiElementPointer);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaBundle.message("quickfix.family.replace.with.magic.constant");
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
      List<String> names = new ArrayList<>();
      for (SmartPsiElementPointer<PsiAnnotationMemberValue> myMemberValuePointer : myMemberValuePointers) {
        PsiAnnotationMemberValue value = myMemberValuePointer.getElement();
        if (value == null) return null;
        names.add(value.getText());
      }
      return Presentation.of(CommonQuickFixBundle.message("fix.replace.with.x", StringUtil.join(names, " | ")));
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression startElement, @NotNull ModPsiUpdater updater) {
      List<PsiAnnotationMemberValue> values = ContainerUtil.map(myMemberValuePointers, SmartPsiElementPointer::getElement);
      String text = StringUtil.join(Collections.nCopies(values.size(), "0"), " | ");
      PsiExpression concatExp = PsiElementFactory.getInstance(context.project()).createExpressionFromText(text, startElement);

      List<PsiLiteralExpression> expressionsToReplace = new ArrayList<>(values.size());
      concatExp.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
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
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          PsiElement bound = expression.bindToElement(resolvedValuesIterator.next());
          JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(bound);
        }
      });
    }
  }
}
