// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class FieldCanBeLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "FieldCanBeLocal";
  public final JDOMExternalizableStringList EXCLUDE_ANNOS = new JDOMExternalizableStringList();
  public boolean IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = true;

  private void doCheckClass(final PsiClass aClass,
                            ProblemsHolder holder,
                            final List<String> excludeAnnos,
                            boolean ignoreFieldsUsedInMultipleMethods) {
    if (aClass.isInterface()) return;
    final PsiField[] fields = aClass.getFields();
    final Set<PsiField> candidates = new LinkedHashSet<>();
    for (PsiField field : fields) {
      if (AnnotationUtil.isAnnotated(field, excludeAnnos, 0)) {
        continue;
      }
      if (field.hasModifierProperty(PsiModifier.PRIVATE) && !(field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL))) {
        candidates.add(field);
      }
    }

    removeFieldsReferencedFromInitializers(aClass, aClass, candidates);
    if (candidates.isEmpty()) return;

    final Set<PsiField> usedFields = new THashSet<>();
    removeReadFields(aClass, candidates, usedFields, ignoreFieldsUsedInMultipleMethods);

    if (candidates.isEmpty()) return;
    final List<ImplicitUsageProvider> implicitUsageProviders = ImplicitUsageProvider.EP_NAME.getExtensionList();

    for (final PsiField field : candidates) {
      if (usedFields.contains(field) && !hasImplicitReadOrWriteUsage(field, implicitUsageProviders)) {
        if (ReferencesSearch.search(field, new LocalSearchScope(aClass)).anyMatch(reference -> {
          final PsiElement element = reference.getElement();
          if (!(element instanceof PsiReferenceExpression)) return false;
          final PsiElement qualifier = ((PsiReferenceExpression)element).getQualifier();
          return qualifier != null && (!(qualifier instanceof PsiThisExpression) || ((PsiThisExpression)qualifier).getQualifier() != null);
        })) {
          continue;
        }
        final String message = InspectionsBundle.message("inspection.field.can.be.local.problem.descriptor");
        final ArrayList<LocalQuickFix> fixes = new ArrayList<>();
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, qualifiedName -> {
          final LocalQuickFix quickFix = SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
            InspectionGadgetsBundle.message("add.0.to.ignore.if.annotated.by.list.quickfix", qualifiedName),
            QuickFixBundle.message("fix.add.special.annotation.family"),
            EXCLUDE_ANNOS, qualifiedName, field);
          fixes.add(quickFix);
          return true;
        });
        final LocalQuickFix fix = createFix();
        if (fix != null) {
          fixes.add(fix);
        }
        holder.registerProblem(field.getNameIdentifier(), message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }
  }

  protected LocalQuickFix createFix() {
    return new ConvertFieldToLocalQuickFix();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel listPanel = SpecialAnnotationsUtil
      .createSpecialAnnotationsListControl(EXCLUDE_ANNOS, InspectionsBundle.message("special.annotations.annotations.list"));

    final JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(new SingleCheckboxOptionsPanel("Ignore fields used in multiple methods", this, "IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS"), BorderLayout.NORTH);
    panel.add(listPanel, BorderLayout.CENTER);
    return panel;
  }


  private static void removeFieldsReferencedFromInitializers(PsiElement aClass, PsiElement root, Set<PsiField> candidates) {
    root.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        if (method.isConstructor()) {
          final PsiCodeBlock body = method.getBody();
          if (body != null) {
            final PsiStatement[] statements = body.getStatements();
            if (statements.length > 0 && statements[0] instanceof PsiExpressionStatement) {
              final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
              if (expression instanceof PsiMethodCallExpression) {
                final PsiMethod resolveMethod = ((PsiMethodCallExpression)expression).resolveMethod();
                if (resolveMethod != null && resolveMethod.isConstructor()) {
                  removeFieldsReferencedFromInitializers(aClass, expression, candidates);
                }
              }
            }
          }
        }
        final PsiDocComment docComment = method.getDocComment();
        if (docComment != null) {
          removeFieldsReferencedFromInitializers(aClass, docComment, candidates);
        }
        //do not go inside method
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        //do not go inside class initializer
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        excludeFieldCandidate(expression);

        super.visitReferenceExpression(expression);
      }

      @Override
      public void visitDocTagValue(PsiDocTagValue value) {
        excludeFieldCandidate(value.getReference());
        super.visitDocTagValue(value);
      }

      private void excludeFieldCandidate(PsiReference ref) {
        if (ref == null) return;
        final PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiField) {
          final PsiField field = (PsiField)resolved;
          if (aClass.equals(field.getContainingClass())) {
            candidates.remove(field);
          }
        }
      }
    });
  }

  private static void removeReadFields(PsiClass aClass,
                                       final Set<? super PsiField> candidates,
                                       final Set<? super PsiField> usedFields,
                                       final boolean ignoreFieldsUsedInMultipleMethods) {
    final Set<PsiField> ignored = new HashSet<>();
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
          checkCodeBlock(body, candidates, usedFields, ignoreFieldsUsedInMultipleMethods, ignored);
        }
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        final PsiElement body = expression.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields, ignoreFieldsUsedInMultipleMethods, ignored);
        }
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);
        checkCodeBlock(initializer.getBody(), candidates, usedFields, ignoreFieldsUsedInMultipleMethods, ignored);
      }
    });
  }

  private static void checkCodeBlock(final PsiElement body,
                                     final Set<? super PsiField> candidates,
                                     Set<? super PsiField> usedFields,
                                     boolean ignoreFieldsUsedInMultipleMethods,
                                     Set<? super PsiField> ignored) {
    try {
      final Ref<Collection<PsiVariable>> writtenVariables = new Ref<>();
      final ControlFlow controlFlow = ControlFlowFactory.getInstance(body.getProject())
          .getControlFlow(body, AllVariablesControlFlowPolicy.getInstance(), false, false);
      final List<PsiVariable> usedVars = ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize());
      for (PsiVariable usedVariable : usedVars) {
        if (usedVariable instanceof PsiField) {
          final PsiField usedField = (PsiField)usedVariable;
          if (!getWrittenVariables(controlFlow, writtenVariables).contains(usedField)) {
            ignored.add(usedField);
          }

          if (!usedFields.add(usedField) && (ignoreFieldsUsedInMultipleMethods || ignored.contains(usedField))) {
            candidates.remove(usedField); //used in more than one code block
          }
        }
      }

      if (candidates.isEmpty()) return;

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

  private static boolean hasImplicitReadOrWriteUsage(final PsiField field, List<ImplicitUsageProvider> implicitUsageProviders) {
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

  private static class ConvertFieldToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiField> {
    @NotNull
    @Override
    protected List<PsiElement> moveDeclaration(@NotNull final Project project, @NotNull final PsiField variable) {
      final Map<PsiCodeBlock, Collection<PsiReference>> refs = new HashMap<>();
      final List<PsiElement> newDeclarations = new ArrayList<>();
      if (!groupByCodeBlocks(ReferencesSearch.search(variable).findAll(), refs)) return newDeclarations;

      PsiElement declaration;
      for (Collection<PsiReference> psiReferences : refs.values()) {
        declaration = super.moveDeclaration(project, variable, psiReferences, false);
        if (declaration != null) newDeclarations.add(declaration);
      }

      if (!newDeclarations.isEmpty()) {
        final PsiElement lastDeclaration = newDeclarations.get(newDeclarations.size() - 1);
        ApplicationManager.getApplication().runWriteAction(() -> deleteSourceVariable(project, variable, lastDeclaration));
      }
      return newDeclarations;
    }

    private static boolean groupByCodeBlocks(final Collection<? extends PsiReference> allReferences, Map<PsiCodeBlock, Collection<PsiReference>> refs) {
      for (PsiReference psiReference : allReferences) {
        final PsiElement element = psiReference.getElement();
        final PsiCodeBlock block = PsiTreeUtil.getTopmostParentOfType(element, PsiCodeBlock.class);
        if (block == null) {
          return false;
        }

        Collection<PsiReference> references = refs.get(block);
        if (references == null) {
          references = new ArrayList<>();
          if (findExistentBlock(refs, psiReference, block, references)) continue;
          refs.put(block, references);
        }
        references.add(psiReference);
      }
      return true;
    }

    private static boolean findExistentBlock(Map<PsiCodeBlock, Collection<PsiReference>> refs,
                                             PsiReference psiReference,
                                             PsiCodeBlock block,
                                             Collection<? super PsiReference> references) {
      for (Iterator<PsiCodeBlock> iterator = refs.keySet().iterator(); iterator.hasNext(); ) {
        PsiCodeBlock codeBlock = iterator.next();
        if (PsiTreeUtil.isAncestor(codeBlock, block, false)) {
          refs.get(codeBlock).add(psiReference);
          return true;
        }
        else if (PsiTreeUtil.isAncestor(block, codeBlock, false)) {
          references.addAll(refs.get(codeBlock));
          iterator.remove();
          break;
        }
      }
      return false;
    }

    @Override
    @Nullable
    protected PsiField getVariable(@NotNull ProblemDescriptor descriptor) {
      return PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
    }

    @Override
    protected void beforeDelete(@NotNull Project project, @NotNull PsiField variable, @NotNull PsiElement newDeclaration) {
    }

    @NotNull
    @Override
    protected String suggestLocalName(@NotNull Project project, @NotNull PsiField field, @NotNull PsiCodeBlock scope) {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);

      final String propertyName = styleManager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
      final String localName = styleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
      return RefactoringUtil.suggestUniqueVariableName(localName, scope, field);
    }
  }
}
