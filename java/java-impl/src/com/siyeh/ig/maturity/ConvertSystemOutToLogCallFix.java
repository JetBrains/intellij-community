// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.java.JavaBundle;
import com.intellij.lang.logging.JvmLogger;
import com.intellij.modcommand.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.logging.JvmLoggingSettingsStorage;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.logging.StringConcatenationArgumentToLogCallInspection;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.lang.logging.UnspecifiedLogger.UNSPECIFIED_LOGGER_ID;
import static com.intellij.psi.CommonClassNames.JAVA_IO_PRINT_STREAM;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_SYSTEM;

public class ConvertSystemOutToLogCallFix extends PsiBasedModCommandAction<PsiMethodCallExpression> {

  private static final CallMatcher myCallMatcher = CallMatcher.instanceCall(JAVA_IO_PRINT_STREAM, "println", "print");
  private static final Set<String> SUPPORTED_LOGGING_SYSTEMS = Set.of("Log4j2", "Slf4j", "Lombok Slf4j", "Lombok Log4j2");

  private final @NotNull List<JvmLogger> myLoggers;

  private final @NotNull String myMethodName;

  private final @Nullable String myLogName;

  private ConvertSystemOutToLogCallFix(@NotNull List<JvmLogger> loggers,
                                       @Nullable String logName,
                                       @NotNull String method,
                                       @NotNull PsiMethodCallExpression logCall) {
    super(logCall);
    myLoggers = loggers;
    myMethodName = method;
    myLogName = logName;
  }

  @Override
  public @NotNull String getFamilyName() {
    if (myLoggers.size() != 1) {
      return InspectionGadgetsBundle.message("convert.system.out.to.log.call.family.name");
    }
    return InspectionGadgetsBundle.message("convert.system.out.to.log.call.name", myLoggers.get(0).toString());
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethodCallExpression originalCall) {
    if (myLoggers.isEmpty()) {
      return ModCommand.nop();
    }

    if (myLogName != null) {
      return ModCommand.psiUpdate(originalCall, logCall -> replaceWithLogCall(logCall, myLogName, myMethodName));
    }

    if (myLoggers.size() > 1) {
      return ModCommand.chooseAction(JavaBundle.message("dialog.title.choose.logger"),
                                     ContainerUtil.map(myLoggers,
                                                       log -> new ConvertSystemOutToLogCallFix(List.of(log), null, myMethodName,
                                                                                               originalCall)));
    }

    return ModCommand.psiUpdate(originalCall, callExpression -> {
      JvmLogger logger = myLoggers.get(0);

      PsiClass upperClass = getUpperClass(callExpression); //not always a good idea for all cases, but asking is too annoying

      if (upperClass == null) {
        return;
      }

      String logFieldName = logger.getLogFieldName(upperClass);
      if (logFieldName == null) {
        return;
      }

      Project project = context.project();
      PsiElement loggerElement = logger.createLogger(project, upperClass);
      if (loggerElement == null) {
        return;
      }
      logger.insertLoggerAtClass(project, upperClass, loggerElement);

      replaceWithLogCall(callExpression, logFieldName, myMethodName);
    });
  }

  private static void replaceWithLogCall(@NotNull PsiMethodCallExpression callExpression,
                                         @NotNull String logName,
                                         @NotNull String logMethodName) {
    PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    if(expressions.length != 1) {
      return;
    }

    PsiExpression expression = expressions[0];
    Project project = callExpression.getProject();

    String arguments = getArguments(expression);

    String text = logName + "." + logMethodName + "(" + arguments + ")";

    CommentTracker tracker = new CommentTracker();
    PsiElement newElement = tracker.replace(callExpression, text);

    if (!(newElement instanceof PsiMethodCallExpression newCall)) {
      return;
    }
    StringConcatenationArgumentToLogCallInspection.LogConcatenationContext context =
      StringConcatenationArgumentToLogCallInspection.getLogConcatenationContext(newCall.getArgumentList().getExpressions());

    if (context == null) {
      return;
    }

    PsiUpdateModCommandQuickFix fix = StringConcatenationArgumentToLogCallInspection.getQuickFix(context.problemType(), context.argument());
    if (fix instanceof StringConcatenationArgumentToLogCallInspection.EvaluatedStringFix evaluatedStringFix) {
      evaluatedStringFix.fix(project, newCall);
    }
  }

  private static @NotNull String getArguments(@NotNull PsiExpression expression) {
    PsiType expressionType = expression.getType();

    if (expressionType==null ||expressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return expression.getText();
    }

    if (InheritanceUtil.isInheritor(expressionType, CommonClassNames.JAVA_LANG_EXCEPTION)) {
      return "\"e: \" , " + expression.getText();
    }

    return "String.valueOf(" + expression.getText() + ")";
  }

  public static @Nullable ModCommandAction createFix(@NotNull PsiMethodCallExpression callExpression,
                                                     @NotNull String method) {
    if (!myCallMatcher.matches(callExpression)) {
      return null;
    }

    PsiReferenceExpression expressionPrint = callExpression.getMethodExpression();
    if (!(expressionPrint.getQualifierExpression() instanceof PsiReferenceExpression expression)) {
      return null;
    }
    String name = expression.getReferenceName();
    if (!HardcodedMethodConstants.OUT.equals(name) &&
        !HardcodedMethodConstants.ERR.equals(name)) {
      return null;
    }

    PsiElement referent = expression.resolve();
    if (!(referent instanceof PsiField systemField)) {
      return null;
    }

    final PsiClass containingClass = systemField.getContainingClass();
    if (containingClass == null) {
      return null;
    }
    final String className = containingClass.getQualifiedName();
    if (!JAVA_LANG_SYSTEM.equals(className)) {
      return null;
    }

    if (callExpression.getArgumentList().getExpressions().length != 1) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForFile(callExpression.getContainingFile());
    if (module == null) {
      return null;
    }

    List<JvmLogger> loggers = JvmLogger.Companion.findSuitableLoggers(module, true);

    if (loggers.isEmpty()) {
      return null;
    }

    PsiClass psiClass = getUpperClass(callExpression);
    if (psiClass == null) {
      return null;
    }

    Project project = callExpression.getProject();
    GlobalSearchScope globalSearchScope = GlobalSearchScope.everythingScope(project);
    List<JvmLogger> loggersWithTargetMethod = new ArrayList<>();
    for (JvmLogger logger : loggers) {
      PsiClass loggerClass = JavaPsiFacade.getInstance(project).findClass(logger.getLoggerTypeName(), globalSearchScope);
      if (loggerClass != null && loggerClass.findMethodsByName(method, true).length > 0 &&
          SUPPORTED_LOGGING_SYSTEMS.contains(logger.getId())) {
        loggersWithTargetMethod.add(logger);
      }
    }

    if (loggersWithTargetMethod.isEmpty()) {
      return null;
    }

    List<PsiField> existedLoggers = new ArrayList<>();
    PsiField[] fields = psiClass.getFields();
    Set<String> allSupportedLoggers = loggersWithTargetMethod.stream().map(log -> log.getLoggerTypeName()).collect(Collectors.toSet());
    for (PsiField field : fields) {
      if (allSupportedLoggers.contains(field.getType().getCanonicalText())) {
        existedLoggers.add(field);
      }
    }

    if (!existedLoggers.isEmpty()) {
      String possibleLogName = existedLoggers.get(0).getName();
      PsiResolveHelper helper = JavaPsiFacade.getInstance(project).getResolveHelper();
      PsiVariable variable = helper.resolveAccessibleReferencedVariable(possibleLogName, callExpression);
      if (variable instanceof PsiField field &&
          allSupportedLoggers.contains(field.getType().getCanonicalText())) {
        return new ConvertSystemOutToLogCallFix(loggersWithTargetMethod, possibleLogName, method, callExpression);
      }
      return null;
    }

    List<JvmLogger> availableLoggers = new ArrayList<>();
    for (JvmLogger logger: loggersWithTargetMethod) {
      String logFieldName = logger.getLogFieldName(psiClass);
      if (logFieldName == null) {
        continue;
      }
      String proposedName = new VariableNameGenerator(callExpression, VariableKind.LOCAL_VARIABLE).byName(logFieldName)
        .generate(true);
      if (!proposedName.equals(logFieldName)) {
        continue;
      }

      if (logger.isPossibleToPlaceLoggerAtClass(psiClass)) {
        availableLoggers.add(logger);
      }
    }

    if (availableLoggers.isEmpty()) {
      return null;
    }

    JvmLoggingSettingsStorage loggingSettingsStorage = project.getService(JvmLoggingSettingsStorage.class);
    JvmLoggingSettingsStorage.State state = loggingSettingsStorage.getState();
    String id = state.getLoggerId();

    if (UNSPECIFIED_LOGGER_ID.equals(id)) {
      return new ConvertSystemOutToLogCallFix(availableLoggers, null, method, callExpression);
    }

    Optional<JvmLogger> chosenLogger = availableLoggers.stream().filter(log -> log.getId().equals(id)).findAny();
    if (chosenLogger.isEmpty()) {
      return null;
    }

    return new ConvertSystemOutToLogCallFix(chosenLogger.stream().toList(), null, method, callExpression);
  }

  private static @Nullable PsiClass getUpperClass(@NotNull PsiElement source) {
    PsiElement target = source;
    PsiElement next = source;
    while (next != null) {
      target = next;
      next = PsiTreeUtil.findFirstParent(target, true,
                                         parent -> parent instanceof PsiClass &&
                                                   !(parent instanceof PsiImplicitClass) &&
                                                   !(parent instanceof PsiAnonymousClass));
    }
    return target instanceof PsiClass ? (PsiClass)target : null;
  }

  public enum PopularLogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR;

    public @NlsSafe String toMethodName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
