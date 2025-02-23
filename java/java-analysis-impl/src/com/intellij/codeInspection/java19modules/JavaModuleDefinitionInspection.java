// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddUsesDirectiveFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixBackedByIntentionAction;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNullElse;

public final class JavaModuleDefinitionInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return !PsiUtil.isModuleFile(holder.getFile()) ? PsiElementVisitor.EMPTY_VISITOR : new JavaElementVisitor() {
      @Override
      public void visitModule(@NotNull PsiJavaModule module) {
        checkUnusedServices(module);
      }

      @Override
      public void visitRequiresStatement(@NotNull PsiRequiresStatement statement) {
        checkModuleReference(statement.getReferenceElement());
      }

      @Override
      public void visitModuleReferenceElement(@NotNull PsiJavaModuleReferenceElement refElement) {
        super.visitModuleReferenceElement(refElement);
        PsiJavaModuleReference ref = refElement.getReference();
        if (refElement.getParent() instanceof PsiPackageAccessibilityStatement &&
            ref != null && ref.multiResolve(true).length == 0) {
          String message = JavaErrorKinds.MODULE_NOT_FOUND.create(refElement).description().toString();
          holder.registerProblem(refElement, message);
        }
      }
      
      @Override
      public void visitImportModuleStatement(@NotNull PsiImportModuleStatement statement) {
        checkModuleReference(statement.getModuleReference());
      }

      @Override
      public void visitPackageAccessibilityStatement(@NotNull PsiPackageAccessibilityStatement statement) {
        if (statement.getRole() != PsiPackageAccessibilityStatement.Role.OPENS) return;
        PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
        if (refElement == null) return;
        JavaPsiModuleUtil.PackageReferenceState state = JavaPsiModuleUtil.checkPackageReference(statement);
        if (state == JavaPsiModuleUtil.PackageReferenceState.VALID) return;

        var kind = state == JavaPsiModuleUtil.PackageReferenceState.PACKAGE_NOT_FOUND
                   ? JavaErrorKinds.MODULE_REFERENCE_PACKAGE_NOT_FOUND
                   : JavaErrorKinds.MODULE_REFERENCE_PACKAGE_EMPTY;
        String message = kind.create(statement).description().toString();
        String packageName = statement.getPackageName();
        Module module = ModuleUtilCore.findModuleForFile(holder.getFile());
        IntentionAction action =
          module == null ? null : QuickFixFactory.getInstance().createCreateClassInPackageInModuleFix(module, packageName);
        holder.problem(refElement, message)
          .maybeFix(action == null ? null : new LocalQuickFixBackedByIntentionAction(action))
          .register();
      }

      private void checkModuleReference(PsiJavaModuleReferenceElement refElement) {
        if (refElement != null) {
          PsiJavaModuleReference ref = refElement.getReference();
          if (ref != null) {
            ResolveResult[] results = ref.multiResolve(true);
            if (results.length > 1 && ref.resolve() == null) {
              holder.registerProblem(refElement, JavaAnalysisBundle.message("module.ambiguous", refElement.getReferenceText()));
            }
          }
        }
      }

      private void checkUnusedServices(@NotNull PsiJavaModule module) {
        Module host = ModuleUtilCore.findModuleForFile(holder.getFile());
        if (host == null) {
          return;
        }
        List<PsiProvidesStatement> provides = JBIterable.from(module.getProvides()).toList();
        if (!provides.isEmpty()) {
          Set<String>
            exports =
            JBIterable.from(module.getExports()).map(PsiPackageAccessibilityStatement::getPackageName).filter(Objects::nonNull).toSet();
          Set<String> uses = JBIterable.from(module.getUses()).map(st -> qName(st.getClassReference())).filter(Objects::nonNull).toSet();
          for (PsiProvidesStatement statement : provides) {

            PsiJavaCodeReferenceElement ref = statement.getInterfaceReference();
            if (ref != null) {
              PsiElement target = ref.resolve();
              if (target instanceof PsiClass && ModuleUtilCore.findModuleForFile(target.getContainingFile()) == host) {
                String className = qName(ref);
                String packageName = StringUtil.getPackageName(className);
                if (!exports.contains(packageName) && !uses.contains(className)) {
                  holder.problem(requireNonNullElse(ref.getReferenceNameElement(), ref),
                                 JavaAnalysisBundle.message("module.service.unused"))
                    .fix(new AddExportsDirectiveFix(module, packageName, ""))
                    .fix(new AddUsesDirectiveFix(module, className))
                    .register();
                }
              }
            }
          }
        }
      }
    };
  }

  private static String qName(PsiJavaCodeReferenceElement ref) {
    return ref != null ? ref.getQualifiedName() : null;
  }
}