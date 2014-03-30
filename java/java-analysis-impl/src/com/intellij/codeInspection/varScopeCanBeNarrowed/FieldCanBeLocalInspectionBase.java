/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.siyeh.InspectionGadgetsBundle;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FieldCanBeLocalInspectionBase extends BaseJavaBatchLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "FieldCanBeLocal";
  public final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();
  public boolean IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = true;

  private void doCheckClass(final PsiClass aClass,
                            ProblemsHolder holder,
                            final List<String> excludeAnnos,
                            boolean ignoreFieldsUsedInMultipleMethods) {
    if (aClass.isInterface()) return;
    final PsiField[] fields = aClass.getFields();
    final Set<PsiField> candidates = new LinkedHashSet<PsiField>();
    for (PsiField field : fields) {
      if (AnnotationUtil.isAnnotated(field, excludeAnnos)) {
        continue;
      }
      if (field.hasModifierProperty(PsiModifier.PRIVATE) && !(field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL))) {
        candidates.add(field);
      }
    }


    removeFieldsReferencedFromInitializers(aClass, candidates);
    if (candidates.isEmpty()) return;

    final Set<PsiField> usedFields = new THashSet<PsiField>();
    removeReadFields(aClass, candidates, usedFields, ignoreFieldsUsedInMultipleMethods);

    if (candidates.isEmpty()) return;
    final ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);

    for (final PsiField field : candidates) {
      if (usedFields.contains(field) && !hasImplicitReadOrWriteUsage(field, implicitUsageProviders)) {
        final String message = InspectionsBundle.message("inspection.field.can.be.local.problem.descriptor");
        final ArrayList<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>();
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, new Processor<String>() {
          @Override
          public boolean process(String qualifiedName) {
            final LocalQuickFix quickFix = SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
              InspectionGadgetsBundle.message("add.0.to.ignore.if.annotated.by.list.quickfix", qualifiedName),
              QuickFixBundle.message("fix.add.special.annotation.family"),
              EXCLUDE_ANNOS, qualifiedName, field);
            fixes.add(quickFix);
            return true;
          }
        });
        final LocalQuickFix fix = createFix();
        if (fix != null) {
          fixes.add(fix);
        }
        holder.registerProblem(field.getNameIdentifier(), message, fixes.toArray(new LocalQuickFix[fixes.size()]));
      }
    }
  }

  protected LocalQuickFix createFix() {
    return null;
  }

  private static void removeFieldsReferencedFromInitializers(final PsiClass aClass, final Set<PsiField> candidates) {
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        //do not go inside method
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        //do not go inside class initializer
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          final PsiField field = (PsiField)resolved;
          if (aClass.equals(field.getContainingClass())) {
            candidates.remove(field);
          }
        }

        super.visitReferenceExpression(expression);
      }
    });
  }

  private static void removeReadFields(PsiClass aClass,
                                       final Set<PsiField> candidates,
                                       final Set<PsiField> usedFields,
                                       final boolean ignoreFieldsUsedInMultipleMethods) {
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!candidates.isEmpty()) super.visitElement(element);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields, ignoreFieldsUsedInMultipleMethods);
        }
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        final PsiElement body = expression.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields, ignoreFieldsUsedInMultipleMethods);
        }
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);
        checkCodeBlock(initializer.getBody(), candidates, usedFields, ignoreFieldsUsedInMultipleMethods);
      }
    });
  }

  private static void checkCodeBlock(final PsiElement body,
                                     final Set<PsiField> candidates,
                                     Set<PsiField> usedFields,
                                     boolean ignoreFieldsUsedInMultipleMethods) {
    try {
      final ControlFlow
        controlFlow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, AllVariablesControlFlowPolicy.getInstance());
      final List<PsiVariable> usedVars = ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize());
      for (PsiVariable usedVariable : usedVars) {
        if (usedVariable instanceof PsiField) {
          final PsiField usedField = (PsiField)usedVariable;
          if (!usedFields.add(usedField) && ignoreFieldsUsedInMultipleMethods) {
            candidates.remove(usedField); //used in more than one code block
          }
        }
      }
      final Ref<Collection<PsiVariable>> writtenVariables = new Ref<Collection<PsiVariable>>();
      final List<PsiReferenceExpression> readBeforeWrites = ControlFlowUtil.getReadBeforeWrite(controlFlow);
      for (final PsiReferenceExpression readBeforeWrite : readBeforeWrites) {
        final PsiElement resolved = readBeforeWrite.resolve();
        if (resolved instanceof PsiField) {
          final PsiField field = (PsiField)resolved;
          if (!isImmutableState(field.getType()) || !PsiUtil.isConstantExpression(field.getInitializer()) || getWrittenVariables(controlFlow, writtenVariables).contains(field)) {
            PsiElement parent = body.getParent();
            if (!(parent instanceof PsiMethod) ||
                !((PsiMethod)parent).isConstructor() ||
                field.getInitializer() == null ||
                field.hasModifierProperty(PsiModifier.STATIC) ||
                !PsiTreeUtil.isAncestor(((PsiMethod)parent).getContainingClass(), field, true)) {
              candidates.remove(field);
            }
          }
        }
      }
    }
    catch (AnalysisCanceledException e) {
      candidates.clear();
    }
  }

  private static boolean isImmutableState(PsiType type) {
    return type instanceof PsiPrimitiveType ||
           PsiPrimitiveType.getUnboxedType(type) != null ||
           Comparing.strEqual(CommonClassNames.JAVA_LANG_STRING, type.getCanonicalText());
  }

  private static Collection<PsiVariable> getWrittenVariables(ControlFlow controlFlow, Ref<Collection<PsiVariable>> writtenVariables) {
    if (writtenVariables.get() == null) {
      writtenVariables.set(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
    }
    return writtenVariables.get();
  }

  private static boolean hasImplicitReadOrWriteUsage(final PsiField field, ImplicitUsageProvider[] implicitUsageProviders) {
    for (ImplicitUsageProvider provider : implicitUsageProviders) {
      if (provider.isImplicitRead(field) || provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CLASS_LAYOUT_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.field.can.be.local.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!EXCLUDE_ANNOS.isEmpty() || !IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS) {
      super.writeSettings(node);
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitJavaFile(PsiJavaFile file) {
        for (PsiClass aClass : file.getClasses()) {
          doCheckClass(aClass, holder, EXCLUDE_ANNOS, IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS);
        }
      }
    };
  }
}
