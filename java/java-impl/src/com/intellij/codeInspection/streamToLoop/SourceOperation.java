/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.StreamToLoopReplacementContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

import static com.intellij.codeInspection.streamToLoop.FunctionHelper.replaceVarReference;

/**
 * @author Tagir Valeev
 */
abstract class SourceOperation extends Operation {
  @Contract(value = " -> true", pure = true)
  @Override
  final boolean changesVariable() {
    return true;
  }

  @NotNull
  @Override
  final String wrap(StreamVariable inVar, StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
    // Cannot inline "result" as wrap may register more beforeSteps
    String result = wrap(outVar, code, context);
    return context.drainBeforeSteps() + result + context.drainAfterSteps();
  }

  abstract String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context);

  @Nullable
  static SourceOperation createSource(PsiMethodCallExpression call, boolean supportUnknownSources) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    PsiType callType = call.getType();
    if(callType == null || PsiType.VOID.equals(callType)) return null;
    PsiMethod method = call.resolveMethod();
    if(method == null) return null;
    String name = method.getName();
    PsiClass aClass = method.getContainingClass();
    if(aClass == null) return null;
    String className = aClass.getQualifiedName();
    if(className == null) return null;
    if ((name.equals("range") || name.equals("rangeClosed")) && args.length == 2 && method.getModifierList().hasExplicitModifier(
      PsiModifier.STATIC) && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
      return new RangeSource(args[0], args[1], name.equals("rangeClosed"));
    }
    if (name.equals("of") && method.getModifierList().hasExplicitModifier(
      PsiModifier.STATIC) && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
      if (method.getParameterList().getParametersCount() != 1) return null;
      if (args.length == 1) {
        PsiType type = args[0].getType();
        PsiType componentType = null;
        if (type instanceof PsiArrayType) {
          componentType = ((PsiArrayType)type).getComponentType();
        }
        else if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)) {
          componentType = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_LANG_ITERABLE, 0, false);
        }
        PsiType elementType = StreamApiUtil.getStreamElementType(callType);
        if (componentType != null && elementType.isAssignableFrom(componentType)) {
          return new ForEachSource(args[0]);
        }
        if (type == null || !elementType.isAssignableFrom(type)) return null;
      }
      return new ExplicitSource(call);
    }
    if (name.equals("generate") && args.length == 1 && method.getModifierList().hasExplicitModifier(
      PsiModifier.STATIC) && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
      FunctionHelper fn = FunctionHelper.create(args[0], 0);
      return fn == null ? null : new GenerateSource(fn, null);
    }
    if (name.equals("iterate") && args.length == 2 && method.getModifierList().hasExplicitModifier(
      PsiModifier.STATIC) && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
      FunctionHelper fn = FunctionHelper.create(args[1], 1);
      return fn == null ? null : new IterateSource(args[0], fn);
    }
    if (name.equals("stream") && args.length == 0 &&
        InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      return new ForEachSource(call.getMethodExpression().getQualifierExpression());
    }
    if (name.equals("stream") && args.length == 1 &&
        CommonClassNames.JAVA_UTIL_ARRAYS.equals(className)) {
      return new ForEachSource(args[0]);
    }
    if (name.equals("stream") &&
        args.length == 3 &&
        CommonClassNames.JAVA_UTIL_ARRAYS.equals(className) &&
        args[0].getType() != null &&
        PsiType.INT.equals(args[1].getType()) &&
        PsiType.INT.equals(args[2].getType())) {
      return new ArraySliceSource(args[0], args[1], args[2]);
    }
    if (supportUnknownSources) {
      PsiType type = StreamApiUtil.getStreamElementType(call.getType(), false);
      if (type != null) {
        return new StreamIteratorSource(call, type);
      }
    }
    return null;
  }

  static class ForEachSource extends SourceOperation {
    private @Nullable PsiExpression myQualifier;

    ForEachSource(@Nullable PsiExpression qualifier) {
      myQualifier = qualifier;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      if(myQualifier != null) {
        myQualifier = replaceVarReference(myQualifier, oldName, newName, context);
      }
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myQualifier);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      if (myQualifier instanceof PsiReferenceExpression) {
        String name = ((PsiReferenceExpression)myQualifier).getReferenceName();
        if(name != null) {
          String singularName = StringUtil.unpluralize(name);
          if(singularName != null && !name.equals(singularName)) {
            outVar.addOtherNameCandidate(singularName);
          }
        }
      }
    }

    @Override
    public String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      PsiExpression iterationParameter = myQualifier == null ? ExpressionUtils
        .getQualifierOrThis(((PsiMethodCallExpression)context.createExpression("stream()")).getMethodExpression()) : myQualifier;
      return context.getLoopLabel() + "for(" + outVar.getDeclaration() + ": " + iterationParameter.getText() + ") {" + code + "}\n";
    }
  }

  static class ExplicitSource extends SourceOperation {
    private PsiMethodCallExpression myCall;

    ExplicitSource(PsiMethodCallExpression call) {
      myCall = call;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myCall = replaceVarReference(myCall, oldName, newName, context);
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myCall.getArgumentList());
    }

    @Override
    public String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      PsiType type = outVar.getType();
      String iterationParameter;
      PsiExpressionList argList = myCall.getArgumentList();
      if (type instanceof PsiPrimitiveType) {
        // Not using argList.getExpressions() here as we want to preserve comments and formatting between the expressions
        PsiElement[] children = argList.getChildren();
        // first and last children are (parentheses), we need to remove them
        iterationParameter = StreamEx.of(children, 1, children.length - 1)
          .map(PsiElement::getText)
          .joining("", "new " + type.getCanonicalText() + "[] {", "}");
      }
      else {
        iterationParameter = "java.util.Arrays.<" + type.getCanonicalText() + ">asList" + argList.getText();
      }
      return context.getLoopLabel() +
             "for(" + outVar.getDeclaration() + ": " + iterationParameter + ") {" + code + "}\n";
    }
  }

  static class GenerateSource extends SourceOperation {
    private FunctionHelper myFn;
    private PsiExpression myLimit;

    GenerateSource(FunctionHelper fn, PsiExpression limit) {
      myFn = fn;
      myLimit = limit;
    }

    @Override
    Operation combineWithNext(Operation next) {
      if(myLimit == null && next instanceof LimitOperation) {
        return new GenerateSource(myFn, ((LimitOperation)next).myLimit);
      }
      return null;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myFn.rename(oldName, newName, context);
      if(myLimit != null) {
        myLimit = replaceVarReference(myLimit, oldName, newName, context);
      }
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      myFn.registerReusedElements(consumer);
      if(myLimit != null) {
        consumer.accept(myLimit);
      }
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      myFn.transform(context);
      String loop = "while(true)";
      if(myLimit != null) {
        String loopIdx = context.registerVarName(Arrays.asList("count", "limit"));
        loop = "for(long "+loopIdx+"="+myLimit.getText()+";"+loopIdx+">0;"+loopIdx+"--)";
      }
      return context.getLoopLabel() +
             loop+"{\n" +
             outVar.getDeclaration(myFn.getText()) + code +
             "}\n";
    }
  }

  static class IterateSource extends SourceOperation {
    private PsiExpression myInitializer;
    private FunctionHelper myFn;

    IterateSource(PsiExpression initializer, FunctionHelper fn) {
      myInitializer = initializer;
      myFn = fn;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myInitializer = replaceVarReference(myInitializer, oldName, newName, context);
      myFn.rename(oldName, newName, context);
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myInitializer);
      myFn.registerReusedElements(consumer);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      myFn.preprocessVariable(context, outVar, 0);
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      myFn.transform(context, outVar.getName());
      return context.getLoopLabel() +
             "for(" + outVar.getDeclaration() + "=" + myInitializer.getText() + ";;" +
             outVar + "=" + myFn.getText() + ") {\n" + code + "}\n";
    }
  }

  static class RangeSource extends SourceOperation {
    private PsiExpression myOrigin;
    private PsiExpression myBound;
    private boolean myInclusive;

    RangeSource(PsiExpression origin, PsiExpression bound, boolean inclusive) {
      myOrigin = origin;
      myBound = bound;
      myInclusive = inclusive;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myOrigin = replaceVarReference(myOrigin, oldName, newName, context);
      myBound = replaceVarReference(myBound, oldName, newName, context);
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myOrigin);
      consumer.accept(myBound);
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      String bound = myBound.getText();
      if(!ExpressionUtils.isSimpleExpression(context.createExpression(bound))) {
        bound = context.declare("bound", outVar.getType().getCanonicalText(), bound);
      }
      String loopVar = outVar.getName();
      String reassign = "";
      if (outVar.isFinal()) {
        loopVar = context.registerVarName(Arrays.asList("i", "j", "idx"));
        reassign = outVar.getDeclaration(loopVar);
      }
      return context.getLoopLabel() +
             "for(" + outVar.getType().getCanonicalText() + " " + loopVar + " = " + myOrigin.getText() + ";" +
             loopVar + (myInclusive ? "<=" : "<") + bound + ";" +
             loopVar + "++) {\n" +
             reassign +
             code + "}\n";
    }
  }

  static class ArraySliceSource extends SourceOperation {
    private @NotNull PsiExpression myArray;
    private @NotNull PsiExpression myOrigin;
    private @NotNull PsiExpression myBound;
    private @NotNull PsiType myArrayType;

    ArraySliceSource(@NotNull PsiExpression array, @NotNull PsiExpression origin, @NotNull PsiExpression bound) {
      myOrigin = origin;
      myBound = bound;
      myArray = array;
      myArrayType = Objects.requireNonNull(myArray.getType());
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myOrigin = replaceVarReference(myOrigin, oldName, newName, context);
      myBound = replaceVarReference(myBound, oldName, newName, context);
      myArray = replaceVarReference(myArray, oldName, newName, context);
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myOrigin);
      consumer.accept(myBound);
      consumer.accept(myArray);
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      String bound = myBound.getText();
      String array = myArray.getText();
      if (!ExpressionUtils.isSimpleExpression(context.createExpression(array))) {
        array = context.declare("array", myArrayType.getCanonicalText(), array);
      }
      if (!ExpressionUtils.isSimpleExpression(context.createExpression(bound))) {
        bound = context.declare("bound", "int", bound);
      }
      String loopVar = context.registerVarName(Arrays.asList("i", "j", "idx"));
      String element = outVar.getDeclaration(array + "[" + loopVar + "]");
      return context.getLoopLabel() +
             "for(" + "int " + loopVar + " = " + myOrigin.getText() + ";" +
             loopVar + "<" + bound + ";" +
             loopVar + "++) {\n" +
             element +
             code + "}\n";
    }
  }

  private static class StreamIteratorSource extends SourceOperation {
    private final String myElementType;
    private PsiMethodCallExpression myCall;

    public StreamIteratorSource(PsiMethodCallExpression call, PsiType type) {
      myCall = call;
      myElementType = type.getCanonicalText();
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myCall = replaceVarReference(myCall, oldName, newName, context);
    }

    @Override
    public void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myCall);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, StreamVariable inVar, StreamVariable outVar) {
      String name = myCall.getMethodExpression().getReferenceName();
      if (name != null) {
        String unpluralized = StringUtil.unpluralize(name);
        if (unpluralized != null && !unpluralized.equals(name)) {
          outVar.addOtherNameCandidate(unpluralized);
        }
      }
    }

    static String getIteratorType(String type) {
      switch(type) {
        case "int":
          return "java.util.PrimitiveIterator.OfInt";
        case "long":
          return "java.util.PrimitiveIterator.OfLong";
        case "double":
          return "java.util.PrimitiveIterator.OfDouble";
        default:
          return CommonClassNames.JAVA_UTIL_ITERATOR+"<"+type+">";
      }
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      String iterator = context.registerVarName(Arrays.asList("it", "iter", "iterator"));
      String declaration = getIteratorType(myElementType) + " " + iterator + "=" + myCall.getText() + ".iterator()";
      String condition = iterator + ".hasNext()";
      return "for(" + declaration + ";" + condition + ";) {\n" +
             outVar.getDeclaration(iterator + ".next()") +
             code + "}\n";
    }
  }
}
