// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.AbstractJavaErrorFixProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddFinallyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromNewFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateInnerClassFromNewFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateInnerClassFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateInnerRecordFromNewFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateParameterFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateRecordFromNewFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateServiceImplementationClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateServiceInterfaceOrClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateTypeParameterFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveClassToModuleFix;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QualifyStaticConstantFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameUnderscoreFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameWrongRefFix;
import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportConstantFix;
import com.intellij.codeInsight.daemon.impl.quickfix.SurroundWithQuotesAnnotationParameterValueFix;
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
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiTypeElement;
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
      sink.accept(new ImportClassFix(ref));
    });
    fixes(JavaErrorKinds.TYPE_UNKNOWN_CLASS, (error, sink) -> {
      PsiJavaCodeReferenceElement element = error.psi().getInnermostComponentReferenceElement();
      if (element != null) {
        registerReferenceFixes(element, sink);
        sink.accept(new ImportClassFix(element));
      }
    });
    fixes(JavaErrorKinds.REFERENCE_AMBIGUOUS, (error, sink) -> registerReferenceFixes(error.psi(), sink));
    fixes(JavaErrorKinds.EXPRESSION_EXPECTED, (error, sink) -> registerReferenceFixes(error.psi(), sink));
    JavaFixesPusher<PsiElement, JavaResolveResult> accessFix = (error, sink) -> {
      if (error.psi() instanceof PsiJavaCodeReferenceElement ref) {
        registerReferenceFixes(ref, sink);
        sink.accept(new ImportClassFix(ref));
      }
    };
    fixes(JavaErrorKinds.CALL_AMBIGUOUS_NO_MATCH, (error, sink) -> registerReferenceFixes(error.psi().getMethodExpression(), sink));
    fixes(JavaErrorKinds.CALL_UNRESOLVED, (error, sink) -> registerReferenceFixes(error.psi().getMethodExpression(), sink));
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
        createFieldFixes = ContainerUtil.map(createFieldFixes, PriorityIntentionActionWrapper::highPriority);
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
    PsiFile containingFile = ref.getContainingFile();
    if (containingFile instanceof PsiJavaCodeReferenceCodeFragment fragment && !fragment.isClassesAccepted()) {
      return;
    }
    List<IntentionAction> fixes = new ArrayList<>();
    OrderEntryFix.registerFixes(ref, fixes);
    fixes.forEach(sink);
    if (PsiUtil.isModuleFile(ref.getContainingFile())) {
      sink.accept(new CreateServiceImplementationClassFix(ref));
      sink.accept(new CreateServiceInterfaceOrClassFix(ref));
      return;
    }
    MoveClassToModuleFix.registerFixes(sink, ref);

    PsiElement refParent = ref.getParent();
    if (ref instanceof PsiReferenceExpression refExpr) {
      if (!(refParent instanceof PsiMethodCallExpression)) {
        createVariableActions(refExpr).forEach(sink);
      }
      sink.accept(new RenameWrongRefFix(refExpr));
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        AddTypeCastFix.registerFix(sink, qualifier, ref);
      }
      BringVariableIntoScopeFix bringToScope = BringVariableIntoScopeFix.fromReference(refExpr);
      if (bringToScope != null) {
        sink.accept(bringToScope.asIntention());
      }
    }
    if (!(refParent instanceof PsiMethodCallExpression)) {
      sink.accept(new StaticImportConstantFix(containingFile, ref));
      sink.accept(new QualifyStaticConstantFix(containingFile, ref));
    }
    SurroundWithQuotesAnnotationParameterValueFix.register(sink, ref);

    if (PsiUtil.isAvailable(JavaFeature.GENERICS, ref)) {
      sink.accept(new CreateTypeParameterFromUsageFix(ref).asIntention());
    }
    createClassActions(ref).forEach(sink);
  }

  private static @NotNull Collection<IntentionAction> createClassActions(@NotNull PsiJavaCodeReferenceElement ref) {
    Collection<IntentionAction> result = new ArrayList<>();
    PsiElement refParent = ref.getParent();
    if (refParent != null && refParent.getParent() instanceof PsiDeconstructionPattern) {
      result.add(new CreateClassFromUsageFix(ref, CreateClassKind.RECORD));
      result.add(new CreateInnerClassFromUsageFix(ref, CreateClassKind.RECORD));
    }
    else {
      PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
      PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(ref, PsiExpressionList.class);

      boolean isNewExpression =
        parent instanceof PsiNewExpression &&
        !(refParent instanceof PsiTypeElement) &&
        (expressionList == null || !PsiTreeUtil.isAncestor(parent, expressionList, false));

      if (isNewExpression) {
        result.add(new CreateClassFromNewFix((PsiNewExpression)parent));
      }
      else {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.CLASS));
      }

      result.add(new CreateClassFromUsageFix(ref, CreateClassKind.INTERFACE));
      if (PsiUtil.isAvailable(JavaFeature.ENUMS, ref)) {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.ENUM));
      }
      if (PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, ref)) {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.ANNOTATION));
      }
      if (PsiUtil.isAvailable(JavaFeature.RECORDS, ref)) {
        if (isNewExpression) {
          result.add(new CreateRecordFromNewFix((PsiNewExpression)parent));
        }
        else {
          result.add(new CreateClassFromUsageFix(ref, CreateClassKind.RECORD));
        }
      }

      if (isNewExpression) {
        result.add(new CreateInnerClassFromNewFix((PsiNewExpression)parent));
        if (PsiUtil.isAvailable(JavaFeature.RECORDS, ref) && ((PsiNewExpression)parent).getQualifier() == null) {
          result.add(new CreateInnerRecordFromNewFix((PsiNewExpression)parent));
        }
      }
      else {
        result.add(new CreateInnerClassFromUsageFix(ref, CreateClassKind.CLASS));
      }
    }
    return result;
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
