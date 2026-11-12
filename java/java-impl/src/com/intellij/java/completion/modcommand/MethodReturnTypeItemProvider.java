// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashSet;
import java.util.Set;
import java.util.function.ObjIntConsumer;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * Completes the return type of a method with the types of the values it returns and their supertypes:
 * {@code Char<caret> foo() { return "x"; }} gets {@code CharSequence}.
 */
@ApiStatus.Internal
@NotNullByDefault
public final class MethodReturnTypeItemProvider extends JavaModCompletionItemProvider {
  public static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE =
    psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMethod.class)
      .andNot(JavaCompletionUtil.AFTER_DOT);

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    PsiElement position = context.getPosition();
    if (!IN_METHOD_RETURN_TYPE.accepts(position)) return;
    processProbableReturnTypes(position, (type, priority) -> sink.accept(JavaTypeCompletionItem.create(type).withPriority(priority)));
  }

  /**
   * Passes the types of the values that the method at {@code position} returns, and their supertypes, each with its priority.
   */
  public static void processProbableReturnTypes(PsiElement position, ObjIntConsumer<? super PsiType> consumer) {
    PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class);
    assert method != null;

    final PsiTypeVisitor<PsiType> eachProcessor = new PsiTypeVisitor<>() {
      private final Set<PsiType> myProcessed = new HashSet<>();

      @Override
      public PsiType visitType(PsiType type) {
        if (!(type instanceof PsiPrimitiveType) && PsiTypesUtil.isDenotableType(type, position) && myProcessed.add(type)) {
          int priority = type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? 1 : 1000 - myProcessed.size();
          consumer.accept(type, priority);
        }
        return type;
      }
    };
    for (PsiType type : getReturnTypeCandidates(method)) {
      eachProcessor.visitType(type);
      ExpectedTypesProvider.processAllSuperTypes(type, eachProcessor, position.getProject(), new HashSet<>(), new HashSet<>());
    }
  }

  private static PsiType[] getReturnTypeCandidates(PsiMethod method) {
    PsiType lub = null;
    boolean hasVoid = false;
    for (PsiReturnStatement statement : PsiUtil.findReturnStatements(method)) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) {
        hasVoid = true;
      }
      else {
        PsiType type = value.getType();
        if (lub == null) {
          lub = type;
        }
        else if (type != null) {
          lub = GenericsUtil.getLeastUpperBound(lub, type, method.getManager());
        }
      }
    }
    if (hasVoid && lub == null) {
      lub = PsiTypes.voidType();
    }
    if (lub instanceof PsiIntersectionType intersectionType) {
      return intersectionType.getConjuncts();
    }
    return lub == null ? PsiType.EMPTY_ARRAY : new PsiType[]{lub};
  }
}
