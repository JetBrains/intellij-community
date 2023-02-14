// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizableStringList;
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
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;

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
      if (!field.isPhysical() || AnnotationUtil.isAnnotated(field, excludeAnnos, 0)) {
        continue;
      }
      if (field.hasModifierProperty(PsiModifier.VOLATILE)) {
        // Assume that fields marked as volatile can be modified concurrently
        // (e.g. if the only method where they are changed is called from several threads)
        continue;
      }
      if (field.hasModifierProperty(PsiModifier.PRIVATE) && !(field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL))) {
        candidates.add(field);
      }
    }

    removeFieldsReferencedFromInitializers(aClass, aClass, candidates);
    if (candidates.isEmpty()) return;

    final Set<PsiField> usedFields = new HashSet<>();
    removeReadFields(aClass, candidates, usedFields, ignoreFieldsUsedInMultipleMethods);

    if (candidates.isEmpty()) return;
    final List<ImplicitUsageProvider> implicitUsageProviders = ImplicitUsageProvider.EP_NAME.getExtensionList();

    final PsiClass scope = findVariableScope(aClass);

    FieldLoop:
    for (final PsiField field : candidates) {
      if (usedFields.contains(field) && !hasImplicitReadOrWriteUsage(field, implicitUsageProviders)) {
        final Query<PsiReference> references = ReferencesSearch.search(field, new LocalSearchScope(scope));
        final Map<PsiCodeBlock, Collection<PsiReference>> refs = new HashMap<>();
        for (PsiReference reference : references.findAll()) {
          final PsiElement element = reference.getElement();
          if (!(element instanceof PsiReferenceExpression)) continue FieldLoop;
          final PsiElement qualifier = ((PsiReferenceExpression)element).getQualifier();
          if (qualifier != null && (!(qualifier instanceof PsiThisExpression) || ((PsiThisExpression)qualifier).getQualifier() != null) ||
              !groupReferenceByCodeBlocks(refs, reference)) {
            continue FieldLoop;
          }
        }
        final String message = JavaBundle.message("inspection.field.can.be.local.problem.descriptor");
        final ArrayList<LocalQuickFix> fixes = new ArrayList<>();
        SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes(field, qualifiedName -> {
          final LocalQuickFix quickFix = SpecialAnnotationsUtilBase.createAddToSpecialAnnotationsListQuickFix(
            InspectionGadgetsBundle.message("add.0.to.ignore.if.annotated.by.list.quickfix", qualifiedName),
            QuickFixBundle.message("fix.add.special.annotation.family"),
            JavaBundle.message("special.annotations.annotations.list"), EXCLUDE_ANNOS, qualifiedName
          );
          fixes.add(quickFix);
          return true;
        });
        fixes.add(new ConvertFieldToLocalQuickFix(refs));
        holder.registerProblem(field.getNameIdentifier(), message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("EXCLUDE_ANNOS", JavaBundle.message("special.annotations.annotations.list"),
                 new JavaClassValidator().annotationsOnly()),
      checkbox("IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS", JavaBundle.message("checkbox.ignore.fields.used.in.multiple.methods"))
    );
  }

  private static @NotNull PsiClass findVariableScope(@NotNull PsiClass containingClass) {
    final PsiClass scope = PsiTreeUtil.getTopmostParentOfType(containingClass, PsiClass.class);
    return scope == null ? containingClass : scope;
  }

  private static void removeFieldsReferencedFromInitializers(PsiElement aClass, PsiElement root, Set<PsiField> candidates) {
    root.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {

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
      public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
        //do not go inside class initializer
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        // do not go inside lambda
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        excludeFieldCandidate(expression);

        super.visitReferenceExpression(expression);
      }

      @Override
      public void visitDocTagValue(@NotNull PsiDocTagValue value) {
        excludeFieldCandidate(value.getReference());
        super.visitDocTagValue(value);
      }

      private void excludeFieldCandidate(PsiReference ref) {
        if (ref == null) return;
        final PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiField field && aClass.equals(field.getContainingClass())) {
          candidates.remove(field);
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
      public void visitElement(@NotNull PsiElement element) {
        if (!candidates.isEmpty()) super.visitElement(element);
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        super.visitMethod(method);

        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields, ignoreFieldsUsedInMultipleMethods, ignored);
        }
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        final PsiElement body = expression.getBody();
        if (body != null) {
          checkCodeBlock(body, candidates, usedFields, ignoreFieldsUsedInMultipleMethods, ignored);
        }
      }

      @Override
      public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
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
      final ControlFlow controlFlow = ControlFlowFactory
          .getControlFlow(body, AllVariablesControlFlowPolicy.getInstance(), ControlFlowOptions.NO_CONST_EVALUATE);
      final List<PsiField> usedVars = ContainerUtil.filterIsInstance(
        ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize()), PsiField.class);
      if (usedVars.isEmpty()) return;

      final Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false);
      for (PsiField usedVariable : usedVars) {
        if (!writtenVariables.contains(usedVariable)) {
          ignored.add(usedVariable);
        }

        if (!usedFields.add(usedVariable) && (ignoreFieldsUsedInMultipleMethods || ignored.contains(usedVariable))) {
          candidates.remove(usedVariable); //used in more than one code block
        }
      }
      if (candidates.isEmpty()) return;

      final List<PsiReferenceExpression> readBeforeWrites = ControlFlowUtil.getReadBeforeWrite(controlFlow);
      for (final PsiReferenceExpression readBeforeWrite : readBeforeWrites) {
        final PsiElement resolved = readBeforeWrite.resolve();
        if (resolved instanceof PsiField field &&
            (!isImmutableState(field.getType()) || !PsiUtil.isConstantExpression(field.getInitializer()) ||
             writtenVariables.contains(field))) {
          PsiElement parent = body.getParent();
          if (parent instanceof PsiMethod method && method.isConstructor() &&
              field.getInitializer() != null && !field.hasModifierProperty(PsiModifier.STATIC) &&
              PsiTreeUtil.isAncestor(method.getContainingClass(), field, true)) {
            continue;
          }
          candidates.remove(field);
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

  private static boolean hasImplicitReadOrWriteUsage(final PsiField field, List<? extends ImplicitUsageProvider> implicitUsageProviders) {
    for (ImplicitUsageProvider provider : implicitUsageProviders) {
      if (provider.isImplicitRead(field) || provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  private static boolean groupByCodeBlocks(final Collection<? extends PsiReference> allReferences,
                                           final Map<PsiCodeBlock, Collection<PsiReference>> refs) {
    for (PsiReference psiReference : allReferences) {
      if (!groupReferenceByCodeBlocks(refs, psiReference)) return false;
    }
    return true;
  }

  private static boolean groupReferenceByCodeBlocks(Map<PsiCodeBlock, Collection<PsiReference>> refs, PsiReference psiReference) {
    final PsiElement element = psiReference.getElement();
    final PsiCodeBlock block = getTopmostBlock(element);
    if (block == null) {
      return false;
    }

    Collection<PsiReference> references = refs.get(block);
    if (references == null) {
      references = new ArrayList<>();
      if (findExistentBlock(refs, psiReference, block, references)) return true;
      refs.put(block, references);
    }
    references.add(psiReference);
    return true;
  }

  @Nullable
  private static PsiCodeBlock getTopmostBlock(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    PsiCodeBlock block = null;
    while (parent != null && !(parent instanceof PsiClass)) {
      if (parent instanceof PsiCodeBlock) block = (PsiCodeBlock)parent;
      parent = parent.getParent();
    }
    return block;
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
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.class.structure");
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
      public void visitClass(@NotNull PsiClass aClass) {
        super.visitClass(aClass);
        doCheckClass(aClass, holder, EXCLUDE_ANNOS, IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS);
      }
    };
  }

  private static final class ConvertFieldToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiField> {
    private final @IntentionName String myName;

    private ConvertFieldToLocalQuickFix(@NotNull Map<PsiCodeBlock, Collection<PsiReference>> refs) {
      final Set<PsiCodeBlock> blocks = refs.keySet();
      final PsiElement block;
      if (blocks.size() == 1) {
        block =
          PsiTreeUtil.getNonStrictParentOfType(blocks.toArray(PsiCodeBlock.EMPTY_ARRAY)[0], PsiClassInitializer.class, PsiMethod.class);
      }
      else {
        block = null;
      }

      myName = determineName(block);
    }

    @NotNull
    private @IntentionName String determineName(@Nullable PsiElement block) {
      if (block instanceof PsiClassInitializer) return JavaBundle.message("inspection.field.can.be.local.quickfix.initializer");
      if (block instanceof PsiMethod) {
        if (((PsiMethod)block).isConstructor()) return JavaBundle.message("inspection.field.can.be.local.quickfix.constructor");
        return JavaBundle.message("inspection.field.can.be.local.quickfix.one.method", ((PsiMethod)block).getName());
      }
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    @Nullable
    protected PsiField getVariable(@NotNull ProblemDescriptor descriptor) {
      return PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
    }

    @NotNull
    @Override
    protected String suggestLocalName(@NotNull Project project, @NotNull PsiField field, @NotNull PsiCodeBlock scope) {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      final String propertyName = styleManager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
      final String localName = styleManager.propertyNameToVariableName(propertyName, VariableKind.LOCAL_VARIABLE);
      return CommonJavaRefactoringUtil.suggestUniqueVariableName(localName, scope, field);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      applyFix(project, previewDescriptor);
      return IntentionPreviewInfo.DIFF;
    }

    @NotNull
    @Override
    protected List<PsiElement> moveDeclaration(@NotNull final Project project, @NotNull final PsiField variable) {
      final Map<PsiCodeBlock, Collection<PsiReference>> refs = new HashMap<>();
      final List<PsiElement> newDeclarations = new ArrayList<>();
      final PsiClass containingClass = variable.getContainingClass();
      if (containingClass == null) return newDeclarations;
      final PsiClass scope = findVariableScope(containingClass);
      if (!groupByCodeBlocks(ReferencesSearch.search(variable,  new LocalSearchScope(scope)).findAll(), refs)) return newDeclarations;
      PsiElement declaration;
      for (Collection<PsiReference> psiReferences : refs.values()) {
        declaration = IntentionPreviewUtils.writeAndCompute(() -> copyVariableToMethodBody(variable, psiReferences));
        if (declaration != null) newDeclarations.add(declaration);
      }
      if (!newDeclarations.isEmpty()) {
        final PsiElement lastDeclaration = newDeclarations.get(newDeclarations.size() - 1);
        IntentionPreviewUtils.write(() -> deleteField(variable, lastDeclaration));
      }
      return newDeclarations;
    }

    private static void deleteField(@NotNull PsiField variable, PsiElement newDeclaration) {
      CommentTracker tracker = new CommentTracker();
      variable.normalizeDeclaration();
      tracker.delete(variable);
      tracker.insertCommentsBefore(newDeclaration);
    }
  }
}
