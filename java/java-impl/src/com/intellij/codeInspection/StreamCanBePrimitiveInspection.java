// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;


import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.dataFlow.NullnessUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

public class StreamCanBePrimitiveInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<String> ourConvertibleTypes = new HashSet<>();

  private static final CallMatcher STREAM = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "stream").parameterCount(0);
  private static final CallMatcher AS_IS_STREAM_CALLS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "sorted", "distinct", "filter", "limit", "skip", "count");
  private static final CallMatcher MAP = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "map").parameterCount(1);
  private static final CallMatcher MAP_TO_X = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "mapToInt").parameterCount(1),
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "mapToLong").parameterCount(1),
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "mapToDouble").parameterCount(1)
  );


  private static final Map<String, ReplacementTransformation> methodNameToReplacementTransformation = new HashMap<>();
  private static final CallMapper<ReplacementStatus> ourReplacementStatusMapper = new CallMapper<>();

  static {
    ourConvertibleTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    ourConvertibleTypes.add(CommonClassNames.JAVA_LANG_LONG);
    ourConvertibleTypes.add(CommonClassNames.JAVA_LANG_DOUBLE);
    List<StreamTransformationProvider> transformers = Arrays.asList(
      new StreamTransformer(),
      new AsIsTransformer(),
      new MappingTransformer(),
      new MappingToXTransformer()
    );
    for (StreamTransformationProvider provider : transformers) {
      register(provider.transformation());
    }
  }

  static void register(TransformationInfo transformation) {
    transformation.matcher.names().forEach(name -> {
      methodNameToReplacementTransformation.put(name, transformation.replacementTransformation);
    });
    ourReplacementStatusMapper.register(transformation.matcher, transformation.replacementMapping);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiType typeParameter = boxedPrimitiveStreamParameter(call.getType());
        if (typeParameter == null) return;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null) {
          // Need to handle all chain only once - from the beginning
          if (boxedPrimitiveStreamParameter(qualifier.getType()) != null) return;
        }
        // TODO work
        boolean nonTrivialReplacementPossible = false;
        for (PsiMethodCallExpression callExpression : new CallChain(call)) {
          ReplacementStatus status = ourReplacementStatusMapper.mapFirst(callExpression);
          if (status == null || status == ReplacementStatus.Impossible) break;
          if (status == ReplacementStatus.Recommended) {
            nonTrivialReplacementPossible = true;
          }
        }
        if (nonTrivialReplacementPossible) {
          holder.registerProblem(call, "Stream can be replaced with primitive", new MigrateToPrimitiveStreamFix());
        }
      }
    };
  }

  private static class CallChain implements Iterable<PsiMethodCallExpression> {
    private final @NotNull PsiMethodCallExpression first;

    private CallChain(@NotNull PsiMethodCallExpression first) {this.first = first;}

    @NotNull
    @Override
    public Iterator<PsiMethodCallExpression> iterator() {
      return new CallChainIterator(first);
    }
  }

  private static class CallChainIterator implements Iterator<PsiMethodCallExpression> {
    PsiMethodCallExpression current;
    boolean nextRetrieved = true;

    public CallChainIterator(@NotNull PsiMethodCallExpression first) {
      this.current = first;
    }

    @Override
    public boolean hasNext() {
      if (!nextRetrieved) {
        current = ExpressionUtils.getCallForQualifier(current);
        nextRetrieved = true;
      }
      return current != null;
    }

    @Override
    public PsiMethodCallExpression next() {
      nextRetrieved = false;
      return current;
    }
  }

  @Nullable
  private static PsiType boxedPrimitiveStreamParameter(@Nullable PsiType type) {
    PsiClassType classType = tryCast(type, PsiClassType.class);
    if (classType == null) return null;
    if (!CommonClassNames.JAVA_UTIL_STREAM_STREAM.equals(classType.rawType().getCanonicalText())) return null;
    if (classType.getParameterCount() != 1) return null;
    PsiType typeParameter = classType.getParameters()[0];
    String typeParameterText = typeParameter.getCanonicalText();
    if (!ourConvertibleTypes.contains(typeParameterText)) return null;
    return typeParameter;
  }

  private static class MigrateToPrimitiveStreamFix implements LocalQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.stream.can.be.primitive.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression callExpression = tryCast(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (callExpression == null) return;
      CallChain callChain = new CallChain(callExpression);
      CommentTracker ct = new CommentTracker();
      StringBuilder sb = new StringBuilder();
      PsiExpression qualifier = callExpression.getMethodExpression().getQualifierExpression();
      if (qualifier != null) {
        sb.append(qualifier.getText());
      } // TODO Probably here comments can be lost, add all tokens
      PsiMethodCallExpression last = callExpression;
      for (PsiMethodCallExpression call : callChain) {
        ReplacementStatus replacementStatus = ourReplacementStatusMapper.mapFirst(call);
        if (replacementStatus == null || replacementStatus == ReplacementStatus.Impossible) break;
        ReplacementTransformation transformation = methodNameToReplacementTransformation.get(call.getMethodExpression().getReferenceName());
        assert transformation != null;

        String replacement = transformation.replace(call, ct);
        assert replacement != null;
        sb.append(replacement);
        last = call;
      }
      ct.replaceAndRestoreComments(last, sb.toString());
    }
  }

  private static class StreamTransformer implements StreamTransformationProvider {
    @Override
    public TransformationInfo transformation() {
      return new TransformationInfo(STREAM, StreamTransformer::generateReplacement, StreamTransformer::getReplacementStatus);
    }

    private static String generateReplacement(PsiMethodCallExpression call, CommentTracker ct) {
      PsiType elementType = StreamApiUtil.getStreamElementType(call.getType());
      if (elementType == null) return null;
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(elementType);
      if (unboxedType == null) return null;
      String operationName = StreamRefactoringUtil.getMapOperationName(elementType, unboxedType);

      StringBuilder sb = new StringBuilder();
      fillWithTextBetweenQualifierAndMethodName(call, sb, ct, true);
      fillWithTextBetweenMethodNameAndArgumentList(call, sb, ct);
      sb.append(ct.text(call.getArgumentList()));
      String varName = JavaCodeStyleManager.getInstance(call.getProject()).suggestUniqueVariableName("v", call.getArgumentList(), true);
      return sb.append(".").append(operationName).append("(").append(varName).append("->").append(varName).append(")").toString();
    }

    private static ReplacementStatus getReplacementStatus(PsiMethodCallExpression call) {
      return isAvailable(call) ? ReplacementStatus.Possible : ReplacementStatus.Impossible;
    }

    private static boolean isAvailable(PsiMethodCallExpression call) {
      if (!STREAM.test(call)) return false;
      PsiType elementType = StreamApiUtil.getStreamElementType(call.getType());
      if (elementType == null) return false;
      return ourConvertibleTypes.contains(elementType.getCanonicalText());
    }
  }

  private static class AsIsTransformer implements StreamTransformationProvider {
    @Override
    public TransformationInfo transformation() {
      return new TransformationInfo(AS_IS_STREAM_CALLS, AsIsTransformer::generateReplacement, AsIsTransformer::getReplacementStatus);
    }

    private static String generateReplacement(PsiMethodCallExpression call, CommentTracker ct) {
      StringBuilder sb = new StringBuilder();
      fillWithTextBetweenQualifierAndMethodName(call, sb, ct, true);
      fillWithTextBetweenMethodNameAndArgumentList(call, sb, ct);
      return sb.append(ct.text(call.getArgumentList())).toString();
    }

    private static ReplacementStatus getReplacementStatus(PsiMethodCallExpression call) {
      return AS_IS_STREAM_CALLS.test(call) ? ReplacementStatus.Recommended : ReplacementStatus.Impossible;
    }
  }

  private static class MappingTransformer implements StreamTransformationProvider {
    @Override
    public TransformationInfo transformation() {
      return new TransformationInfo(MAP, MappingTransformer::generateReplacement, MappingTransformer::getReplacementStatus);
    }

    private static String generateReplacement(PsiMethodCallExpression call, CommentTracker ct) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      assert qualifier != null;
      PsiType inElementType = StreamApiUtil.getStreamElementType(qualifier.getType());
      if (TypeConversionUtil.isPrimitiveWrapper(inElementType)) {
        inElementType = PsiPrimitiveType.getUnboxedType(inElementType);
      }
      PsiType outElementType = StreamApiUtil.getStreamElementType(call.getType());
      if (TypeConversionUtil.isPrimitiveWrapper(outElementType)) {
        // TODO nullness
        outElementType = PsiPrimitiveType.getUnboxedType(outElementType);
      }
      if (outElementType == null) return null;
      StringBuilder sb = new StringBuilder();
      fillWithTextBetweenQualifierAndMethodName(call, sb, ct, false);
      String mapOperationName = StreamRefactoringUtil.getMapOperationName(inElementType, outElementType);
      sb.append(mapOperationName);
      fillWithTextBetweenMethodNameAndArgumentList(call, sb, ct);
      return sb.append(ct.text(call.getArgumentList())).toString();
    }

    private static ReplacementStatus getReplacementStatus(PsiMethodCallExpression call) {
      return MAP.test(call) ? ReplacementStatus.Recommended : ReplacementStatus.Impossible;
    }
  }

  private static class MappingToXTransformer implements StreamTransformationProvider {
    @Override
    public TransformationInfo transformation() {
      return new TransformationInfo(MAP_TO_X, MappingToXTransformer::generateReplacement, MappingToXTransformer::getReplacementStatus);
    }

    private static String generateReplacement(PsiMethodCallExpression call, CommentTracker ct) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      assert qualifier != null;
      PsiType inElementType = StreamApiUtil.getStreamElementType(qualifier.getType());
      if (TypeConversionUtil.isPrimitiveWrapper(inElementType)) {
        inElementType = PsiPrimitiveType.getUnboxedType(inElementType);
      }
      PsiType outElementType = StreamApiUtil.getStreamElementType(call.getType());

      if (isIdMapping(call)) return "";


      StringBuilder sb = new StringBuilder();
      fillWithTextBetweenQualifierAndMethodName(call, sb, ct, false);
      String mapOperationName = StreamRefactoringUtil.getMapOperationName(inElementType, outElementType);
      sb.append(mapOperationName);
      fillWithTextBetweenMethodNameAndArgumentList(call, sb, ct);
      return sb.append(ct.text(call.getArgumentList())).toString();
    }

    private static boolean isIdMapping(PsiMethodCallExpression call) {
      PsiExpressionList argumentList = call.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) return false;
      PsiExpression argument = arguments[0];
      PsiLambdaExpression lambda = tryCast(argument, PsiLambdaExpression.class);
      if (lambda == null) return false;
      PsiReferenceExpression referenceExpression = tryCast(lambda.getBody(), PsiReferenceExpression.class);
      if (referenceExpression == null) return false;
      return lambda.getParameterList().getParametersCount() == 1 &&
             ExpressionUtils.isReferenceTo(referenceExpression, lambda.getParameterList().getParameters()[0]);
    }

    private static ReplacementStatus getReplacementStatus(PsiMethodCallExpression call) {
      return MAP_TO_X.test(call) ? ReplacementStatus.Recommended : ReplacementStatus.Impossible;
    }
  }

  private static void fillWithTextBetweenQualifierAndMethodName(@NotNull PsiMethodCallExpression call,
                                                                StringBuilder sb,
                                                                CommentTracker ct,
                                                                boolean includeMethodName) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    PsiElement current = qualifier != null ? qualifier.getNextSibling() : call.getMethodExpression().getFirstChild();
    PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
    while (current != null) {
      sb.append(ct.text(current));
      current = current.getNextSibling();
      if (!includeMethodName && current == nameElement) break;
    }
  }

  private static void fillWithTextBetweenMethodNameAndArgumentList(@NotNull PsiMethodCallExpression call,
                                                                   StringBuilder sb,
                                                                   CommentTracker ct) {
    PsiElement current = call.getMethodExpression().getNextSibling();
    PsiExpressionList argumentList = call.getArgumentList();
    while (current != argumentList) {
      sb.append(ct.text(current));
      current = current.getNextSibling();
    }
  }

  @FunctionalInterface
  interface ReplacementTransformation {
    /**
     * @param call to replace
     * @return replacement text only for current call, without qualifier, but with dot, if required
     */
    String replace(PsiMethodCallExpression call, CommentTracker tracker);
  }

  private static class TransformationInfo {
    final CallMatcher matcher;
    final ReplacementTransformation replacementTransformation;
    final Function<PsiMethodCallExpression, ReplacementStatus> replacementMapping;

    private TransformationInfo(CallMatcher matcher,
                               ReplacementTransformation replacementTransformation,
                               Function<PsiMethodCallExpression, ReplacementStatus> mapping) {
      this.matcher = matcher;
      this.replacementTransformation = replacementTransformation;
      replacementMapping = mapping;
    }
  }

  interface StreamTransformationProvider {
    TransformationInfo transformation();
  }

  private enum ReplacementStatus {
    Possible,
    Recommended,
    Impossible
  }
}
