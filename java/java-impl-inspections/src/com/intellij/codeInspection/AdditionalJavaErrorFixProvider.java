// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.AbstractJavaErrorFixProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddFinallyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateParameterFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameUnderscoreFix;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassJava10Fix;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.streamMigration.SimplifyForEachInspection;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.lang.java.request.CreateFieldFromUsage;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.ACCESS_PACKAGE_LOCAL;
import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.ACCESS_PRIVATE;
import static com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds.ACCESS_PROTECTED;

/**
 * Some quick-fixes not accessible from the java.analysis module are registered here.
 */
public final class AdditionalJavaErrorFixProvider extends AbstractJavaErrorFixProvider {
  public AdditionalJavaErrorFixProvider() {
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new SimplifyForEachInspection.ForEachNonFinalFix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
    fixes(JavaErrorKinds.SYNTAX_ERROR, (error, info) -> registerErrorElementFixes(info, error.psi()));
    fix(JavaErrorKinds.UNDERSCORE_IDENTIFIER_UNNAMED, error -> {
      if (error.psi().getParent() instanceof PsiReferenceExpression ref && "_".equals(ref.getReferenceName())) {
        return new RenameUnderscoreFix(ref);
      }
      return null;
    });
    fix(JavaErrorKinds.UNSUPPORTED_FEATURE, error -> {
      JavaFeature context = error.context();
      if (context == JavaFeature.IMPLICIT_CLASSES) {
        return ImplicitToExplicitClassBackwardMigrationInspection.createFix(error.psi());
      }
      else if (context == JavaFeature.TEXT_BLOCKS && error.psi().getParent() instanceof PsiLiteralExpression expression) {
        return new TextBlockBackwardMigrationInspection.ReplaceWithRegularStringLiteralFix(expression);
      }
      return null;
    });
    fixes(JavaErrorKinds.REFERENCE_UNRESOLVED, (error, sink) -> {
      PsiJavaCodeReferenceElement ref = error.psi();
      if (!PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, ref)) {
        sink.accept(MigrateFromJavaLangIoInspection.createCanBeIOFix(ref));
      }
      registerReferenceFixes(ref, sink);
    });
    fixes(JavaErrorKinds.TYPE_UNKNOWN_CLASS, (error, sink) -> {
      PsiJavaCodeReferenceElement element = error.psi().getInnermostComponentReferenceElement();
      if (element != null) {
        registerReferenceFixes(element, sink);
      }
    });
    JavaFixesPusher<PsiElement, JavaResolveResult> accessFix = (error, sink) -> {
      if (error.psi() instanceof PsiJavaCodeReferenceElement ref) {
        registerReferenceFixes(ref, sink);
      }
    };
    fixes(ACCESS_PRIVATE, accessFix);
    fixes(ACCESS_PROTECTED, accessFix);
    fixes(ACCESS_PACKAGE_LOCAL, accessFix);
  }

  private static @NotNull Collection<IntentionAction> createVariableActions(@NotNull PsiReferenceExpression refExpr) {
    Collection<IntentionAction> result = new ArrayList<>();
    boolean isQualified = refExpr.isQualified();
    if (!isQualified) {
      result.add(new CreateLocalFromUsageFix(refExpr).asIntention());
    }

    VariableKind kind = CreateLocalFromUsageFix.getKind(refExpr);

    if (!(refExpr instanceof PsiMethodReferenceExpression)) {
      List<IntentionAction> createFieldFixes = CreateFieldFromUsage.generateActions(refExpr);
      if (kind == VariableKind.FIELD) {
        createFieldFixes = ContainerUtil.map(createFieldFixes, fix -> PriorityIntentionActionWrapper.highPriority(fix));
      }
      result.addAll(createFieldFixes);
    }

    if (!isQualified) {
      IntentionAction createParameterFix = new CreateParameterFromUsageFix(refExpr);
      result.add(kind == VariableKind.PARAMETER ? PriorityIntentionActionWrapper.highPriority(createParameterFix) : createParameterFix);
    }

    return result;
  }
  
  private static void registerReferenceFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull Consumer<? super CommonIntentionAction> sink) {
    sink.accept(new ImportClassFix(ref));
    if (ref instanceof PsiReferenceExpression refExpr) {
      createVariableActions(refExpr).forEach(sink);
    }
  }

  private static void registerErrorElementFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                                @NotNull PsiErrorElement errorElement) {
    PsiElement parent = errorElement.getParent();
    String description = errorElement.getErrorDescription();
    if (description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      info.accept(new InsertMissingTokenFix(";"));
      if (parent instanceof PsiExpressionStatement statement) {
        HighlightFixUtil.registerFixesForExpressionStatement(info, statement);
      }
    }
    if (parent instanceof PsiTryStatement tryStatement && description.equals(JavaPsiBundle.message("expected.catch.or.finally"))) {
      info.accept(new AddExceptionToCatchFix(false));
      info.accept(new AddFinallyFix(tryStatement));
    }
    if (parent instanceof PsiSwitchLabelStatementBase && description.equals(JavaPsiBundle.message("expected.colon.or.arrow"))) {
      PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(parent, PsiSwitchBlock.class);
      if (switchBlock != null && switchBlock.getBody() != null) {
        boolean isOld = false;
        boolean isRule = false;
        for (@NotNull PsiElement child : switchBlock.getBody().getChildren()) {
          if (child instanceof PsiSwitchLabeledRuleStatement) {
            isRule = true;
          }
          if (child instanceof PsiSwitchLabelStatement && !PsiTreeUtil.isAncestor(child, parent, false)) {
            isOld = true;
          }
        }
        if (isOld) {
          info.accept(new InsertMissingTokenFix(":", true));
        }
        if (isRule) {
          info.accept(new InsertMissingTokenFix(" ->", true));
        }
      }
    }
  }
}
