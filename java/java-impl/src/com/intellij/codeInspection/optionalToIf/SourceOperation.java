// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.codeInspection.streamToLoop.ChainVariable;
import com.intellij.codeInspection.streamToLoop.FunctionHelper;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;


abstract class SourceOperation implements Operation {

  final PsiType myType;
  final String mySourceName;

  @Contract(pure = true)
  SourceOperation(@NotNull PsiType type, @Nullable String sourceName) {
    myType = type;
    mySourceName = sourceName;
  }

  @Nullable
  static SourceOperation create(@NotNull String name, @NotNull PsiType type, @NotNull PsiExpression[] args) {
    if ("empty".equals(name) && args.length == 0) {
      return new Empty(type);
    }
    if ("of".equals(name) && args.length == 1) {
      return new Of(type, args[0]);
    }
    if ("ofNullable".equals(name) && args.length == 1) {
      return new OfNullable(type, args[0]);
    }

    return null;
  }

  @NotNull
  @Override
  public ChainVariable getOutVar(@NotNull ChainVariable inVar) {
    return mySourceName == null ? new ChainVariable(myType) : new ChainVariable(myType, mySourceName);
  }

  static class Of extends SourceOperation {

    private PsiExpression myArg;

    @Contract(pure = true)
    Of(@NotNull PsiType type, @NotNull PsiExpression arg) {
      super(type, SourceOperation.getSourceName(arg));
      myArg = arg;
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName, @NotNull OptionalToIfContext context) {
      myArg = FunctionHelper.replaceVarReference(myArg, oldName, newName, context);
    }

    @Nullable
    @Override
    public String generate(@NotNull ChainVariable inVar,
                           @NotNull ChainVariable outVar,
                           @NotNull String code,
                           @NotNull OptionalToIfContext context) {
      if (SourceOperation.getSourceName(myArg) != null) {
        return "if(" + outVar.getName() + " == null) throw new java.lang.NullPointerException();" +
               code;
      }
      return outVar.getDeclaration(myArg.getText()) +
             "if(" + outVar.getName() + " == null) throw new java.lang.NullPointerException();" +
             code;
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      if (SourceOperation.getSourceName(myArg) == null) FunctionHelper.suggestFromExpression(outVar, context.getProject(), myArg);
    }
  }

  @Nullable
  private static String getSourceName(@NotNull PsiExpression source) {
    PsiReference reference = tryCast(source, PsiReference.class);
    if (reference == null) return null;
    PsiVariable variable = tryCast(reference.resolve(), PsiVariable.class);
    return variable == null ? null : variable.getName();
  }

  static class OfNullable extends SourceOperation {

    private PsiExpression myArg;

    @Contract(pure = true)
    OfNullable(PsiType type, PsiExpression arg) {
      super(type, SourceOperation.getSourceName(arg));
      myArg = arg;
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName, @NotNull OptionalToIfContext context) {
      myArg = FunctionHelper.replaceVarReference(myArg, oldName, newName, context);
    }

    @Nullable
    @Override
    public String generate(@NotNull ChainVariable inVar,
                           @NotNull ChainVariable outVar,
                           @NotNull String code,
                           @NotNull OptionalToIfContext context) {
      if (SourceOperation.getSourceName(myArg) != null) {
        return context.generateNotNullCondition(outVar.getName(), code);
      }
      return outVar.getDeclaration(myArg.getText()) +
             context.generateNotNullCondition(outVar.getName(), code);
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      if (SourceOperation.getSourceName(myArg) == null) FunctionHelper.suggestFromExpression(outVar, context.getProject(), myArg);
    }
  }

  static class Empty extends SourceOperation {

    Empty(PsiType type) {super(type, null);}

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      outVar.addBestNameCandidate("empty");
    }

    @Nullable
    @Override
    public String generate(@NotNull ChainVariable inVar,
                           @NotNull ChainVariable outVar,
                           @NotNull String code,
                           @NotNull OptionalToIfContext context) {
      return outVar.getDeclaration("null") +
             context.generateNotNullCondition(outVar.getName(), code);
    }
  }
}
