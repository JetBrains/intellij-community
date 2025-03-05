// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.codeInspection.streamToLoop.ChainVariable;
import com.intellij.codeInspection.streamToLoop.FunctionHelper;
import com.intellij.psi.PsiExpression;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class TerminalOperation implements Operation {

  @Override
  public @NotNull ChainVariable getOutVar(@NotNull ChainVariable inVar) {
    return inVar;
  }

  static @Nullable TerminalOperation create(String name, PsiExpression[] args) {
    if ("get".equals(name) && args.length == 0) {
      return new Get();
    }
    if ("orElse".equals(name) && args.length == 1) {
      return new OrElse(args[0].getText());
    }
    if ("ifPresentOrElse".equals(name) && args.length == 2) {
      FunctionHelper ifPresentFn = FunctionHelper.create(args[0], 1);
      if (ifPresentFn == null) return null;
      FunctionHelper orElseFn = FunctionHelper.create(args[1], 0);
      return orElseFn == null ? null : new IfPresentOrElse(ifPresentFn, orElseFn);
    }
    if ("ifPresent".equals(name) && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new IfPresent(fn);
    }
    if (("isPresent".equals(name) || "isEmpty".equals(name)) && args.length == 0) {
      return new IsPresent("isEmpty".equals(name));
    }
    if ("orElseGet".equals(name) && args.length == 1) {
      FunctionHelper myFn = FunctionHelper.create(args[0], 0);
      return myFn == null ? null : new OrElseGet(myFn);
    }
    if ("orElseThrow".equals(name)) {
      if (args.length == 0) return new OrElseThrow(null);
      FunctionHelper myFn = args.length == 1 ? FunctionHelper.create(args[0], 0) : null;
      return myFn == null ? null : new OrElseThrow(myFn);
    }
    if ("stream".equals(name) && args.length == 0) {
      return new Stream();
    }

    return null;
  }

  static class Get extends TerminalOperation {

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      context.setElseBranch("throw new java.util.NoSuchElementException(\"No value present\");");
      return context.createResult(outVar.getName());
    }
  }

  static class OrElse extends TerminalOperation {

    private final String myArg;

    @Contract(pure = true)
    OrElse(String arg) {
      myArg = arg;
    }

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      context.setInitializer(myArg);
      return context.createResult(outVar.getName());
    }
  }

  static class IfPresent extends TerminalOperation {

    private final FunctionHelper myFn;

    IfPresent(FunctionHelper fn) {
      myFn = fn;
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      myFn.preprocessVariable(context, inVar, 0);
    }

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      myFn.transform(context, inVar.getName());
      return "{\n" + myFn.getStatementText() + "\n}\n";
    }
  }

  static class IfPresentOrElse extends TerminalOperation {

    private final FunctionHelper myIfPresentFn;
    private final FunctionHelper myElseFn;

    @Contract(pure = true)
    IfPresentOrElse(FunctionHelper ifPresentFn, FunctionHelper elseFn) {
      myIfPresentFn = ifPresentFn;
      myElseFn = elseFn;
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      myIfPresentFn.preprocessVariable(context, outVar, 0);
      outVar.addBestNameCandidate("result");
    }

    @Override
    public @NotNull ChainVariable getOutVar(@NotNull ChainVariable inVar) {
      return new ChainVariable(inVar.getType());
    }

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      myElseFn.transform(context);
      myIfPresentFn.transform(context, outVar.getName());
      context.addBeforeStep(outVar.getDeclaration("null"));
      context.addAfterStep("if(" + outVar.getName() + "==null){\n" +
                           "{\n" + myElseFn.getStatementText() + "\n}" +
                           "}" +
                           "else{\n" +
                           "{\n" + myIfPresentFn.getStatementText() + "\n}" +
                           "}\n");
      return outVar.getName() + "=" + inVar.getName() + ";";
    }
  }

  static class IsPresent extends TerminalOperation {

    private final boolean myIsInverted;

    IsPresent(boolean isInverted) {
      myIsInverted = isInverted;
    }

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      context.setInitializer(String.valueOf(myIsInverted));
      return context.createResult(String.valueOf(!myIsInverted));
    }
  }

  static class OrElseGet extends TerminalOperation {

    private final FunctionHelper myFn;

    OrElseGet(FunctionHelper fn) {
      myFn = fn;
    }

    @Override
    public @NotNull ChainVariable getOutVar(@NotNull ChainVariable inVar) {
      return new ChainVariable(inVar.getType());
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      outVar.addBestNameCandidate("result");
    }

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      myFn.transform(context);
      if (!SideEffectChecker.mayHaveSideEffects(myFn.getExpression())) {
        context.setInitializer(myFn.getText());
        return context.createResult(inVar.getName());
      }
      context.addBeforeStep(outVar.getDeclaration("null"));
      context.addAfterStep("if(" + outVar.getName() + "==null){\n" +
                           outVar.getName() + "=" + myFn.getStatementText() +
                           "\n}" +
                           context.createResult(outVar.getName()));
      return outVar.getName() + "=" + inVar.getName() + ";";
    }
  }

  static class OrElseThrow extends TerminalOperation {

    private final FunctionHelper myFn;

    OrElseThrow(FunctionHelper fn) {myFn = fn;}

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      if (myFn == null) {
        context.setElseBranch("throw new java.util.NoSuchElementException(\"No value present\");");
      }
      else {
        myFn.transform(context);
        context.setElseBranch("throw " + myFn.getStatementText());
      }
      return context.createResult(outVar.getName());
    }
  }

  static class Stream extends TerminalOperation {

    @Override
    public @Nullable String generate(@NotNull ChainVariable inVar,
                                     @NotNull ChainVariable outVar,
                                     @NotNull String code,
                                     @NotNull OptionalToIfContext context) {
      context.setInitializer("java.util.stream.Stream.empty()");
      return context.createResult("java.util.stream.Stream.of(" + outVar.getName() + ")");
    }
  }
}
