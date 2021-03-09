// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.siyeh.ig.psiutils.MethodCallUtils.getQualifierMethodCall;

public class ReplaceInefficientStreamCountInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher STREAM_COUNT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "count").parameterCount(0);
  private static final CallMatcher COLLECTION_STREAM =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "stream").parameterCount(0);
  private static final CallMatcher STREAM_FLAT_MAP =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "flatMap").parameterTypes(
      CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION);
  private static final CallMatcher STREAM_FILTER =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "filter").parameterCount(1);
  private static final CallMatcher STREAM_PEEK =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "peek").parameterCount(1);
  private static final CallMatcher STREAM_LIMIT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "limit").parameterCount(1);

  private static final CallMapper<CountFix> FIX_MAPPER = new CallMapper<CountFix>()
    .register(COLLECTION_STREAM, call -> new CountFix(SimplificationMode.COLLECTION_SIZE))
    .register(STREAM_FLAT_MAP, call -> doesFlatMapCallCollectionStream(call) ? new CountFix(SimplificationMode.SUM) : null)
    .register(STREAM_FILTER, call -> extractComparisonWithZero(call) != null ? new CountFix(SimplificationMode.ANY_MATCH) : null)
    .register(STREAM_FILTER, call -> extractComparisonWithZeroEq(call) != null ? new CountFix(SimplificationMode.NONE_MATCH) : null);

  private static final Logger LOG = Logger.getInstance(ReplaceInefficientStreamCountInspection.class);

  private static final String SIZE_METHOD = "size";
  private static final String STREAM_METHOD = "stream";

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        if (STREAM_COUNT.test(methodCall)) {
          PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
          CountFix fix = FIX_MAPPER.mapFirst(qualifierCall);
          if (fix == null &&
              !(STREAM_LIMIT.test(qualifierCall) && ExpressionUtils.isOne(qualifierCall.getArgumentList().getExpressions()[0])) &&
              !hasPeekCallBefore(methodCall)) {
            if (extractComparisonWithZero(methodCall) != null) {
              fix = new CountFix(SimplificationMode.IS_PRESENT);
            } else if (extractComparisonWithZeroEq(methodCall) != null) {
              final SimplificationMode simplificationMode;
              if (PsiUtil.getLanguageLevel(methodCall).isAtLeast(LanguageLevel.JDK_11)) {
                simplificationMode = SimplificationMode.IS_EMPTY;
              } else {
                simplificationMode = SimplificationMode.NOT_IS_PRESENT;
              }
              fix = new CountFix(simplificationMode);
            }
          }
          if (fix != null) {
            PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
            LOG.assertTrue(nameElement != null);
            holder.registerProblem(methodCall, nameElement.getTextRange().shiftRight(-methodCall.getTextOffset()), fix.getMessage(), fix);
          }
        }
      }
    };
  }

  @Nullable
  private static PsiBinaryExpression extractComparisonWithZero(PsiMethodCallExpression call) {
    PsiBinaryExpression binary = extractBinary(call);
    if (binary == null) return null;
    IElementType tokenType = binary.getOperationTokenType();
    if(ExpressionUtils.isZero(binary.getLOperand()) && (tokenType == JavaTokenType.LT || tokenType == JavaTokenType.NE) ||
       ExpressionUtils.isZero(binary.getROperand()) && (tokenType == JavaTokenType.GT || tokenType == JavaTokenType.NE)) {
      return binary;
    }
    return null;
  }

  @Nullable
  private static PsiBinaryExpression extractComparisonWithZeroEq(PsiMethodCallExpression call) {
    PsiBinaryExpression binary = extractBinary(call);
    if (binary == null) return null;
    IElementType tokenType = binary.getOperationTokenType();
    if(ExpressionUtils.isZero(binary.getLOperand()) && tokenType == JavaTokenType.EQEQ ||
       ExpressionUtils.isZero(binary.getROperand()) && tokenType == JavaTokenType.EQEQ) {
      return binary;
    }
    return null;
  }

  @Nullable
  private static PsiBinaryExpression extractBinary(PsiMethodCallExpression call) {
    final PsiMethodCallExpression countCall;
    if (STREAM_FILTER.test(call)) {
      countCall = ExpressionUtils.getCallForQualifier(call);
    } else if (STREAM_COUNT.test(call)) {
      countCall = call;
    } else {
      countCall = null;
    }
    if(countCall == null) return null;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(countCall.getParent());
    if(parent == null) return null;
    return ObjectUtils.tryCast(parent, PsiBinaryExpression.class);
  }

  static boolean doesFlatMapCallCollectionStream(PsiMethodCallExpression flatMapCall) {
    PsiElement function = flatMapCall.getArgumentList().getExpressions()[0];
    if (function instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)function;
      if (!STREAM_METHOD.equals(methodRef.getReferenceName())) return false;
      PsiMethod method = ObjectUtils.tryCast(methodRef.resolve(), PsiMethod.class);
      if (method != null && STREAM_METHOD.equals(method.getName()) && method.getParameterList().isEmpty()) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_UTIL_COLLECTION.equals(containingClass.getQualifiedName())) {
          return true;
        }
      }
    }
    else if (function instanceof PsiLambdaExpression) {
      PsiExpression expression = extractLambdaReturnExpression((PsiLambdaExpression)function);
      return expression instanceof PsiMethodCallExpression && COLLECTION_STREAM.test((PsiMethodCallExpression)expression);
    }
    return false;
  }

  @Nullable
  private static PsiExpression extractLambdaReturnExpression(PsiLambdaExpression lambda) {
    PsiElement lambdaBody = lambda.getBody();
    PsiExpression expression = null;
    if (lambdaBody instanceof PsiExpression) {
      expression = (PsiExpression)lambdaBody;
    }
    else if (lambdaBody instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)lambdaBody).getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
        expression = ((PsiReturnStatement)statements[0]).getReturnValue();
      }
    }
    return PsiUtil.skipParenthesizedExprDown(expression);
  }

  private static boolean hasPeekCallBefore(@NotNull PsiMethodCallExpression call) {
    PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
    while (qualifierCall != null && !STREAM_PEEK.test(qualifierCall)) {
      qualifierCall = getQualifierMethodCall(qualifierCall);
    }
    return qualifierCall != null;
  }

  private enum SimplificationMode {
    SUM("Stream.flatMap().count()", "Stream.mapToLong().sum()"),
    COLLECTION_SIZE("Collection.stream().count()", "Collection.size()"),
    ANY_MATCH("Stream().filter().count() > 0", "stream.anyMatch()"),
    NONE_MATCH("Stream().filter().count() == 0", "stream.noneMatch()"),
    IS_PRESENT("stream.count() > 0", "stream.findAny().isPresent()"),
    NOT_IS_PRESENT("stream.count() == 0", "!stream.findAny().isPresent()"),
    IS_EMPTY("stream.count() == 0", "stream.findAny().isEmpty()");

    private final @NonNls @NotNull String myOld;
    private final @NonNls @NotNull String myNew;

    SimplificationMode(@NonNls @NotNull String old, @NonNls @NotNull String aNew) {
      myOld = old;
      myNew = aNew;
    }


    public @Nls String getName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", myOld, myNew);
    }

    public @InspectionMessage String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.x.with.y", myOld, myNew);
    }
  }

  private static class CountFix implements LocalQuickFix {
    private final SimplificationMode mySimplificationMode;

    CountFix(SimplificationMode simplificationMode) {
      mySimplificationMode = simplificationMode;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return mySimplificationMode.getName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.replace.inefficient.stream.count");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression countCall = (PsiMethodCallExpression)element;
      PsiElement countName = countCall.getMethodExpression().getReferenceNameElement();
      if (countName == null) return;
      PsiMethodCallExpression qualifierCall = getQualifierMethodCall(countCall);
      switch (mySimplificationMode) {
        case SUM:
          replaceFlatMap(countName, qualifierCall);
          break;
        case COLLECTION_SIZE:
          replaceSimpleCount(countCall, qualifierCall);
          break;
        case ANY_MATCH:
          replaceFilterCountComparison(qualifierCall, true);
          break;
        case NONE_MATCH:
          replaceFilterCountComparison(qualifierCall, false);
          break;
        case IS_PRESENT:
          replaceSimpleCountComparison(countCall, true);
          break;
        case NOT_IS_PRESENT:
        case IS_EMPTY:
          replaceSimpleCountComparison(countCall, false);
          break;
      }
    }

    private static void replaceFilterCountComparison(PsiMethodCallExpression filterCall, boolean isAnyMatch) {
      if(!STREAM_FILTER.test(filterCall)) return;
      PsiBinaryExpression comparison = isAnyMatch? extractComparisonWithZero(filterCall) : extractComparisonWithZeroEq(filterCall);
      if(comparison == null) return;
      String filterText = filterCall.getArgumentList().getExpressions()[0].getText();
      PsiExpression filterQualifier = filterCall.getMethodExpression().getQualifierExpression();
      if(filterQualifier == null) return;
      String base = filterQualifier.getText();
      CommentTracker ct = new CommentTracker();
      ct.markUnchanged(filterQualifier);
      ct.replaceAndRestoreComments(comparison, base + "." + (isAnyMatch? "anyMatch" : "noneMatch") + "(" + filterText + ")");
    }

    private static void replaceSimpleCount(PsiMethodCallExpression countCall, PsiMethodCallExpression qualifierCall) {
      if (!COLLECTION_STREAM.test(qualifierCall)) return;
      PsiReferenceExpression methodExpression = qualifierCall.getMethodExpression();
      ExpressionUtils.bindCallTo(qualifierCall, SIZE_METHOD);
      boolean addCast = true;
      PsiElement toReplace = countCall;
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(countCall.getParent());
      if(parent instanceof PsiExpressionStatement) {
        addCast = false;
      } else if(parent instanceof PsiTypeCastExpression) {
        PsiTypeElement castElement = ((PsiTypeCastExpression)parent).getCastType();
        if(castElement != null && castElement.getType() instanceof PsiPrimitiveType) {
          addCast = false;
          if(PsiType.INT.equals(castElement.getType())) {
            toReplace = parent;
          }
        }
      }
      CommentTracker ct = new CommentTracker();
      PsiReferenceParameterList parameterList = methodExpression.getParameterList();
      if(parameterList != null) {
        ct.delete(parameterList);
      }
      String replacementText = (addCast ? "(long) " : "") + ct.text(methodExpression)+"()";
      PsiElement replacement = ct.replaceAndRestoreComments(toReplace, replacementText);
      if (replacement instanceof PsiTypeCastExpression && RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)replacement)) {
        RemoveRedundantCastUtil.removeCast((PsiTypeCastExpression)replacement);
      }
    }

    private static void replaceFlatMap(PsiElement countName, PsiMethodCallExpression qualifierCall) {
      if (!STREAM_FLAT_MAP.test(qualifierCall)) return;
      PsiElement flatMapName = qualifierCall.getMethodExpression().getReferenceNameElement();
      if (flatMapName == null) return;
      PsiElement parameter = qualifierCall.getArgumentList().getExpressions()[0];
      PsiElement streamCallName = null;
      if (parameter instanceof PsiMethodReferenceExpression) {
        PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)parameter;
        streamCallName = methodRef.getReferenceNameElement();
      }
      else if (parameter instanceof PsiLambdaExpression) {
        PsiExpression expression = extractLambdaReturnExpression((PsiLambdaExpression)parameter);
        if (expression instanceof PsiMethodCallExpression) {
          streamCallName = ((PsiMethodCallExpression)expression).getMethodExpression().getReferenceNameElement();
        }
      }
      if (streamCallName == null || !streamCallName.getText().equals("stream")) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(qualifierCall.getProject());
      streamCallName.replace(factory.createIdentifier("size"));
      flatMapName.replace(factory.createIdentifier("mapToLong"));
      countName.replace(factory.createIdentifier("sum"));
      PsiReferenceParameterList parameterList = qualifierCall.getMethodExpression().getParameterList();
      if(parameterList != null) {
        parameterList.delete();
      }
    }

    private static void replaceSimpleCountComparison(PsiMethodCallExpression countCall, boolean isPresent) {
      PsiBinaryExpression comparison = isPresent ? extractComparisonWithZero(countCall) : extractComparisonWithZeroEq(countCall);
      if (comparison == null) return;
      PsiExpression countQualifier = countCall.getMethodExpression().getQualifierExpression();
      if (countQualifier == null) return;
      String base = countQualifier.getText();
      CommentTracker ct = new CommentTracker();
      if (isPresent) {
        ct.replaceAndRestoreComments(comparison, base + "." + "findAny().isPresent()");
      } else {
        if (PsiUtil.getLanguageLevel(countCall).isAtLeast(LanguageLevel.JDK_11)) {
          ct.replaceAndRestoreComments(comparison, base + "." + "findAny().isEmpty()");
        } else {
          ct.replaceAndRestoreComments(comparison, "!" + base + "." + "findAny().isPresent()");
        }
      }

    }

    public @InspectionMessage String getMessage() {
      return mySimplificationMode.getMessage();
    }
  }
}
