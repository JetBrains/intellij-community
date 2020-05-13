// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.typeMigration.usageInfo.OverriddenUsageInfo;
import com.intellij.refactoring.typeMigration.usageInfo.OverriderUsageInfo;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

/**
 * @author db
 */
public class TypeMigrationLabeler {
  private static final Logger LOG = Logger.getInstance(TypeMigrationLabeler.class);
  private boolean myShowWarning = true;
  private volatile MigrateException myException;
  private final Semaphore myDialogSemaphore = new Semaphore();
  private final Project myProject;

  public TypeMigrationRules getRules() {
    return myRules;
  }

  private final TypeMigrationRules myRules;
  private final Function<PsiElement, PsiType> myMigrationRootTypeFunction;
  @Nullable private final Set<PsiElement> myAllowedRoots;
  private TypeEvaluator myTypeEvaluator;
  private final LinkedHashMap<PsiElement, Object> myConversions;
  private final Map<Pair<SmartPsiElementPointer<PsiExpression>, PsiType>, TypeMigrationUsageInfo> myFailedConversions;
  private LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> myMigrationRoots;
  private final LinkedHashMap<TypeMigrationUsageInfo, PsiType> myNewExpressionTypeChange;
  private final LinkedHashMap<TypeMigrationUsageInfo, PsiClassType> myClassTypeArgumentsChange;

  private TypeMigrationUsageInfo[] myMigratedUsages;

  private TypeMigrationUsageInfo myCurrentRoot;
  private final Map<TypeMigrationUsageInfo, HashSet<Pair<TypeMigrationUsageInfo, PsiType>>> myRootsTree =
    new HashMap<>();
  private final Map<Pair<TypeMigrationUsageInfo, TypeMigrationUsageInfo>, Set<PsiElement>> myRootUsagesTree = new HashMap<>();
  private final Set<TypeMigrationUsageInfo> myProcessedRoots = new HashSet<>();
  private final Set<PsiTypeParameter> myDisappearedTypeParameters = new HashSet<>();

  public TypeMigrationLabeler(TypeMigrationRules rules, PsiType rootType, Project project) {
    this(rules, Functions.constant(rootType), null, project);
  }

  public TypeMigrationLabeler(TypeMigrationRules rules,
                              Function<PsiElement, PsiType> migrationRootTypeFunction,
                              PsiElement @Nullable("any root accepted if null") [] allowedRoots,
                              Project project) {
    myRules = rules;
    myMigrationRootTypeFunction = migrationRootTypeFunction;
    myAllowedRoots = allowedRoots == null ? null : ContainerUtil.set(allowedRoots);

    myConversions = new LinkedHashMap<>();
    myFailedConversions = new LinkedHashMap<>();
    myNewExpressionTypeChange = new LinkedHashMap<>();
    myClassTypeArgumentsChange = new LinkedHashMap<>();
    myProject = project;
  }

  public boolean hasFailedConversions() {
    return !myFailedConversions.isEmpty();
  }

  public Function<PsiElement, PsiType> getMigrationRootTypeFunction() {
    return myMigrationRootTypeFunction;
  }

  public String[] getFailedConversionsReport() {
    final String[] report = new String[myFailedConversions.size()];
    int j = 0;

    for (final Pair<SmartPsiElementPointer<PsiExpression>, PsiType> p : myFailedConversions.keySet()) {
      final PsiExpression element = p.getFirst().getElement();
      LOG.assertTrue(element != null);
      final PsiType type = element.getType();
      report[j++] = "Cannot convert type of expression <b>" + StringUtil.escapeXmlEntities(element.getText()) + "</b>" +
                    (type != null
                     ? " from <b>" + StringUtil.escapeXmlEntities(type.getCanonicalText()) + "</b>" +
                       " to <b>" + StringUtil.escapeXmlEntities(p.getSecond().getCanonicalText()) + "</b>"
                     : "")
                    + "<br>";
    }

    return report;
  }

  public UsageInfo[] getFailedUsages(final TypeMigrationUsageInfo root) {
    return map2Usages(ContainerUtil.mapNotNull(myFailedConversions.entrySet(),
                                               entry -> entry.getValue().equals(root) ? entry.getKey() : null));
  }

  public UsageInfo[] getFailedUsages() {
    return map2Usages(myFailedConversions.keySet());
  }

  private static UsageInfo @NotNull [] map2Usages(Collection<? extends Pair<SmartPsiElementPointer<PsiExpression>, PsiType>> usages) {
    return ContainerUtil
      .map2Array(usages, new UsageInfo[usages.size()], pair -> {
        final PsiExpression expr = pair.getFirst().getElement();
        LOG.assertTrue(expr != null);
        return new UsageInfo(expr) {
          @Override
          @Nullable
          public String getTooltipText() {
            final PsiType type = expr.isValid() ? expr.getType() : null;
            if (type == null) return null;
            return "Cannot convert type of the expression from " +
                   type.getCanonicalText() + " to " + pair.getSecond().getCanonicalText();
          }
        };
      });
  }

  public TypeMigrationUsageInfo[] getMigratedUsages() {
    final LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> declarations = getTypeEvaluator().getMigratedDeclarations();
    final TypeMigrationUsageInfo[] usages = new TypeMigrationUsageInfo[declarations.size() + myConversions.size() + myNewExpressionTypeChange.size() + myClassTypeArgumentsChange.size()];

    int j = 0;

    for (final PsiElement element : myConversions.keySet()) {
      final Object conv = myConversions.get(element);
      usages[j++] = new TypeMigrationUsageInfo(element) {
        @Override
        public String getTooltipText() {
          if (conv instanceof String) {   //todo
            final String conversion = (String)conv;
            return "Replaced with " + conversion.replaceAll("\\$", element.getText());
          }
          else {
            return "Replaced with " + conv.toString();
          }
        }

        @Override
        public boolean isExcluded() {
          if (conv instanceof TypeConversionDescriptorBase) return ((TypeConversionDescriptorBase)conv).getRoot().isExcluded();
          return super.isExcluded();
        }

        @Override
        public TypeMigrationUsageInfo getOwnerRoot() {
          return conv instanceof TypeConversionDescriptorBase ? ((TypeConversionDescriptorBase)conv).getRoot() : null;
        }
      };
    }

    for (final Pair<TypeMigrationUsageInfo, PsiType> p : declarations) {
      final TypeMigrationUsageInfo element = p.getFirst();
      usages[j++] = element;
    }

    for (TypeMigrationUsageInfo info : myClassTypeArgumentsChange.keySet()) {
      usages[j++] = info;
    }

    for (final TypeMigrationUsageInfo expr : myNewExpressionTypeChange.keySet()) {
      usages[j++] = expr;
    }
    return sortMigratedUsages(usages);
  }

  private TypeMigrationUsageInfo[] sortMigratedUsages(TypeMigrationUsageInfo[] infos) {
    final DFSTBuilder<TypeMigrationUsageInfo> builder = new DFSTBuilder<>(GraphGenerator.generate(
      new InboundSemiGraph<TypeMigrationUsageInfo>() {
        @NotNull
        @Override
        public Collection<TypeMigrationUsageInfo> getNodes() {
          final Set<TypeMigrationUsageInfo> infos = new HashSet<>();
          for (Map.Entry<TypeMigrationUsageInfo, HashSet<Pair<TypeMigrationUsageInfo, PsiType>>> entry : myRootsTree.entrySet()) {
            infos.add(entry.getKey());
            infos.addAll(ContainerUtil.map(entry.getValue(), pair -> pair.getFirst()));
          }
          return infos;
        }

        @NotNull
        @Override
        public Iterator<TypeMigrationUsageInfo> getIn(TypeMigrationUsageInfo n) {
          final HashSet<Pair<TypeMigrationUsageInfo, PsiType>> rawNodes = myRootsTree.get(n);
          if (rawNodes == null) {
            return Collections.emptyIterator();
          }
          final List<TypeMigrationUsageInfo> in =
            ContainerUtil.map(rawNodes, pair -> pair.getFirst());
          return in.iterator();
        }
      }));
    final Comparator<TypeMigrationUsageInfo> cmp = builder.comparator();

    Arrays.sort(infos, (info1, info2) -> {
      final TypeMigrationUsageInfo i1 = info1.getOwnerRoot();
      final TypeMigrationUsageInfo i2 = info2.getOwnerRoot();
      if (i1 == null && i2 == null) {
        return 0;
      }
      if (i1 == null) {
        return 1;
      }
      if (i2 == null) {
        return -1;
      }
      final PsiElement element1 = info1.getElement();
      final PsiElement element2 = info2.getElement();
      LOG.assertTrue(element1 != null && element2 != null);
      if (element1.equals(element2)) {
        return 0;
      }
      final TextRange range1 = element1.getTextRange();
      final TextRange range2 = element2.getTextRange();
      if (range1.contains(range2)) {
        return 1;
      }
      if (range2.contains(range1)) {
        return -1;
      }

      final int res = cmp.compare(i1, i2);
      if (res != 0) {
        return res;
      }
      return Integer.compare(range2.getStartOffset(), range1.getStartOffset());
    });

    return infos;
  }

  MigrationProducer createMigratorFor(UsageInfo[] usages) {
    final Map<UsageInfo, Object> conversions = new HashMap<>();
    for (UsageInfo usage : usages) {
      final Object conversion = getConversion(usage.getElement());
      if (conversion != null) {
        conversions.put(usage, conversion);
      }
    }
    return new MigrationProducer(conversions);
  }

  @Nullable
  public <T> T getSettings(Class<T> aClass) {
    return myRules.getConversionSettings(aClass);
  }

  class MigrationProducer {
    private final Map<UsageInfo, Object> myRemainConversions;
    private final MultiMap<PsiTypeElement, TypeMigrationUsageInfo> myVariableMigration = new MultiMap<PsiTypeElement, TypeMigrationUsageInfo>() {
      @NotNull
      @Override
      protected Map<PsiTypeElement, Collection<TypeMigrationUsageInfo>> createMap() {
        return new THashMap<>();
      }
    };

    private MigrationProducer(Map<UsageInfo, Object> conversions) {
      myRemainConversions = conversions;
    }

    public void change(@NotNull final TypeMigrationUsageInfo usageInfo,
                       @NotNull Consumer<? super PsiNewExpression> consumer) {
      final PsiElement element = usageInfo.getElement();
      if (element == null) return;
      final Project project = element.getProject();
      if (element instanceof PsiExpression) {
        final PsiExpression expression = (PsiExpression)element;
        if (element instanceof PsiNewExpression) {
          for (Map.Entry<TypeMigrationUsageInfo, PsiType> info : myNewExpressionTypeChange.entrySet()) {
            final PsiElement expressionToReplace = info.getKey().getElement();
            if (expression.equals(expressionToReplace)) {
              final PsiNewExpression newExpression =
                TypeMigrationReplacementUtil.replaceNewExpressionType(project, (PsiNewExpression)expressionToReplace, info);
              if (newExpression != null) {
                consumer.consume(newExpression);
              }
            }
          }
        }
        final Object conversion = myRemainConversions.get(usageInfo);
        if (conversion != null) {
          myRemainConversions.remove(usageInfo);
          TypeMigrationReplacementUtil.replaceExpression(expression, project, conversion, myTypeEvaluator);
        }
      } else if (element instanceof PsiReferenceParameterList) {
        for (Map.Entry<TypeMigrationUsageInfo, PsiClassType> entry : myClassTypeArgumentsChange.entrySet()) {
          if (element.equals(entry.getKey().getElement())) { //todo check null
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            try {
              element.getParent().replace(factory.createReferenceElementByType(entry.getValue()));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
      else if ((element instanceof PsiField || element instanceof PsiLocalVariable) &&
               isMultiVariableDeclaration((PsiVariable)element)) {
        final PsiTypeElement typeElement = ((PsiVariable)element).getTypeElement();
        myVariableMigration.putValue(typeElement, usageInfo);
      }
      else {
        TypeMigrationReplacementUtil.migrateMemberOrVariableType(element, project, getTypeEvaluator().getType(usageInfo));
        if (usageInfo instanceof OverriddenUsageInfo) {
          final String migrationName = ((OverriddenUsageInfo)usageInfo).getMigrateMethodName();
          if (migrationName != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
              if (element.isValid()) {
                new RenameProcessor(project, element, migrationName, false, false).run();
              }
            });
          }
        }
      }
    }

    public void flush() {
      for (Map.Entry<PsiTypeElement, Collection<TypeMigrationUsageInfo>> entry : myVariableMigration.entrySet()) {
        final PsiTypeElement typeElement = entry.getKey();
        if (!typeElement.isValid()) continue;
        final Collection<TypeMigrationUsageInfo> migrations = entry.getValue();
        if (migrations.size() != 1) {
          MultiMap<PsiType, PsiVariable> variablesByMigrationType = new MultiMap<>();
          for (TypeMigrationUsageInfo migration : migrations) {
            final PsiElement var = migration.getElement();
            if (!(var instanceof PsiLocalVariable || var instanceof PsiField)) {
              continue;
            }
            final PsiType type = getTypeEvaluator().getType(migration);
            variablesByMigrationType.putValue(type, (PsiVariable)var);
          }
          if (variablesByMigrationType.size() == 1) {
            final Map.Entry<PsiType, Collection<PsiVariable>> migrationTypeAndVariables =
              ContainerUtil.getFirstItem(variablesByMigrationType.entrySet());
            LOG.assertTrue(migrationTypeAndVariables != null);
            final PsiVariable[] variables = PsiTreeUtil.getChildrenOfType(typeElement.getParent().getParent(), PsiVariable.class);
            if (variables != null && variables.length == migrationTypeAndVariables.getValue().size()) {
              try {
                PsiType migrationType = migrationTypeAndVariables.getKey();
                final Project project = variables[0].getProject();
                migrationType = TypeMigrationReplacementUtil.revalidateType(migrationType, project);
                typeElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElement(migrationType));
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
              continue;
            }
          }
        }
        for (TypeMigrationUsageInfo info : entry.getValue()) migrateMultiDeclarationVariable(info);
      }
    }

    private void migrateMultiDeclarationVariable(TypeMigrationUsageInfo varUsageInfo) {
      final PsiElement var = varUsageInfo.getElement();
      if (!(var instanceof PsiLocalVariable || var instanceof PsiField)) return;
      ((PsiVariable) var).normalizeDeclaration();
      TypeMigrationReplacementUtil.migrateMemberOrVariableType(var, var.getProject(), getTypeEvaluator().getType(varUsageInfo));
    }

    Object getConversion(UsageInfo info) {
      return myRemainConversions.remove(info);
    }

    private boolean isMultiVariableDeclaration(PsiVariable variable) {
      final PsiElement parent = variable.getParent();
      LOG.assertTrue(parent != null);
      final PsiVariable[] variables = PsiTreeUtil.getChildrenOfType(parent, PsiVariable.class);
      LOG.assertTrue(variables != null);
      return variables.length != 1;
    }
  }

  void postProcessNewExpression(@NotNull PsiNewExpression expression) {
    TypeMigrationReplacementUtil.tryToReplaceWithDiamond(expression, null);
  }

  @Nullable
  Object getConversion(PsiElement element) {
    return myConversions.get(element);
  }

  public TypeMigrationUsageInfo[] getMigratedUsages(boolean autoMigrate, final PsiElement... roots) {
    if (myMigratedUsages == null) {
      myShowWarning = autoMigrate;
      migrate(autoMigrate, roots);
      myMigratedUsages = getMigratedUsages();
    }
    return myMigratedUsages;
  }

  @Nullable
  public Set<PsiElement> getTypeUsages(final TypeMigrationUsageInfo element, final TypeMigrationUsageInfo currentRoot) {
    return myRootUsagesTree.get(Pair.create(element, currentRoot));
  }

  void convertExpression(final PsiExpression expr, final PsiType toType, final PsiType fromType, final boolean isCovariantPosition) {
    final TypeConversionDescriptorBase conversion = myRules.findConversion(fromType, toType, expr instanceof PsiMethodCallExpression ? ((PsiMethodCallExpression)expr).resolveMethod() : null, expr,
                                                     isCovariantPosition, this);

    if (conversion == null) {
      markFailedConversion(Pair.create(fromType, toType), expr);
    }
    else {
      setConversionMapping(expr, conversion);
    }
  }

  public void migrateExpressionType(final PsiExpression expr, final PsiType migrationType, final PsiElement place, boolean alreadyProcessed, final boolean isCovariant) {
    PsiType originalType = expr.getType();

    if (originalType == null || originalType.equals(migrationType)) return;

    if (originalType.equals(PsiType.NULL)) {
      if (migrationType instanceof PsiPrimitiveType) {
        markFailedConversion(Pair.create(originalType, migrationType), expr);
        return;
      }
      if (place instanceof PsiVariable) {
        PsiType type = ((PsiVariable)place).getType();
        if (((PsiVariable)place).getInitializer() == expr && myRules.shouldConvertNull(type, migrationType, expr)) {
          convertExpression(expr, migrationType, type, isCovariant);
        }
      }
      return;
    }

    if (expr instanceof PsiConditionalExpression) {
      final PsiConditionalExpression condExpr = (PsiConditionalExpression)expr;
      for (PsiExpression e : ContainerUtil.newArrayList(condExpr.getThenExpression(), condExpr.getElseExpression())) {
        if (e != null) {
          migrateExpressionType(e, migrationType, place, alreadyProcessed, false);
        }
      }
      getTypeEvaluator().setType(new TypeMigrationUsageInfo(expr), migrationType);
      return;
    } else if (expr instanceof PsiClassObjectAccessExpression) {
      if (!TypeConversionUtil.isAssignable(migrationType, expr.getType())) {
        markFailedConversion(Pair.create(expr.getType(), migrationType), expr);
        return;
      }
    } else if (expr instanceof PsiArrayInitializerExpression && migrationType instanceof PsiArrayType) {
      final PsiExpression[] initializers = ((PsiArrayInitializerExpression)expr).getInitializers();
      for (PsiExpression initializer : initializers) {
        migrateExpressionType(initializer, ((PsiArrayType)migrationType).getComponentType(), expr, alreadyProcessed, true);
      }
      getTypeEvaluator().setType(new TypeMigrationUsageInfo(expr), migrationType);
      return;
    } else if (expr instanceof PsiArrayAccessExpression) {
      migrateExpressionType(((PsiArrayAccessExpression)expr).getArrayExpression(), migrationType.createArrayType(), place, alreadyProcessed, isCovariant);
      return;
    }
    else if (expr instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)expr).resolve();
      if (resolved != null) {
        if (!addMigrationRoot(resolved, migrationType, place, alreadyProcessed, !isCovariant)) {
          convertExpression(expr, migrationType, getTypeEvaluator().evaluateType(expr), isCovariant);
        }
      }
      return;
    }
    else if (expr instanceof PsiMethodCallExpression) {
      final PsiMethod resolved = ((PsiMethodCallExpression)expr).resolveMethod();
      if (resolved != null) {
        if (!addMigrationRoot(resolved, migrationType, place, alreadyProcessed, !isCovariant)) {
          convertExpression(expr, migrationType, getTypeEvaluator().evaluateType(expr), isCovariant);
        }
      }
      return;
    }
    else if (expr instanceof PsiNewExpression) {
      if (originalType.getArrayDimensions() == migrationType.getArrayDimensions()) {
        if (migrationType.getArrayDimensions() > 0) {
          final PsiType elementType = ((PsiArrayType)migrationType).getComponentType();

          final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)expr).getArrayInitializer();

          if (arrayInitializer != null) {
            final PsiExpression[] initializers = arrayInitializer.getInitializers();
            for (int i = initializers.length - 1; i >= 0; i--) {
              migrateExpressionType(initializers[i], elementType, place, alreadyProcessed, true);
            }
          }

          if (isGenericsArrayType(elementType)){
            markFailedConversion(Pair.create(originalType, migrationType), expr);
            return;
          }

          final TypeMigrationUsageInfo usageInfo = new TypeMigrationUsageInfo(expr);
          usageInfo.setOwnerRoot(myCurrentRoot);
          myNewExpressionTypeChange.put(usageInfo, migrationType);
          getTypeEvaluator().setType(new TypeMigrationUsageInfo(expr), migrationType);
          return;
        } else {
          if (migrationType instanceof PsiClassType && originalType instanceof PsiClassType && ((PsiClassType)migrationType).rawType().isAssignableFrom(((PsiClassType)originalType).rawType())) {
            final PsiClass originalClass = PsiUtil.resolveClassInType(originalType);
            if (originalClass instanceof PsiAnonymousClass) {
              originalType = ((PsiAnonymousClass)originalClass).getBaseClassType();
            }
            final PsiType type = TypeEvaluator.substituteType(migrationType, originalType, true, ((PsiClassType)originalType).resolveGenerics().getElement(),
                                                              JavaPsiFacade.getElementFactory(expr.getProject()).createType(((PsiClassType)originalType).resolve(), PsiSubstitutor.EMPTY));
            if (type != null){
              final TypeMigrationUsageInfo usageInfo = new TypeMigrationUsageInfo(expr);
              usageInfo.setOwnerRoot(myCurrentRoot);
              myNewExpressionTypeChange.put(usageInfo, type);
              getTypeEvaluator().setType(new TypeMigrationUsageInfo(expr), type);
              return;
            }
          }
        }
      }

    }
    else if (expr instanceof PsiLambdaExpression) {
      //TODO conversion of lambda expression now works incorrectly [Dmitry Batkovich]
      return;
    }

    convertExpression(expr, migrationType, originalType, isCovariant);
  }

  private static boolean isGenericsArrayType(final PsiType elementType) {
    if (elementType instanceof PsiClassType && ((PsiClassType)elementType).hasParameters()) {
      return true;
    } else if (elementType instanceof PsiArrayType) {
      final PsiType componentType = ((PsiArrayType)elementType).getComponentType();
      return isGenericsArrayType(componentType);
    }
    return false;
  }

  boolean addMigrationRoot(PsiElement element, PsiType type, final PsiElement place, boolean alreadyProcessed, final boolean isContraVariantPosition) {
    return addMigrationRoot(element, type, place, alreadyProcessed, isContraVariantPosition, false);
  }

  boolean addMigrationRoot(PsiElement element,
                           PsiType type,
                           final PsiElement place,
                           boolean alreadyProcessed,
                           final boolean isContraVariantPosition,
                           final boolean userDefinedType) {
    if (myAllowedRoots != null && !myAllowedRoots.contains(element)) {
      return false;
    }
    if (type.equals(PsiType.NULL)) {
      return false;
    }
    final PsiElement resolved = Util.normalizeElement(element);
    if (!canBeRoot(resolved, myRules.getSearchScope())) {
      return false;
    }
    final PsiType originalType = getElementType(resolved);
    LOG.assertTrue(originalType != null);
    type = userDefinedType ? type : TypeEvaluator.substituteType(type, originalType, isContraVariantPosition);

    if (userDefinedType) {
      Set<PsiTypeParameter> disappearedTypeParameters = getTypeParameters(originalType);
      disappearedTypeParameters.removeAll(getTypeParameters(type));
      myDisappearedTypeParameters.addAll(disappearedTypeParameters);
    }
    else if (typeContainsTypeParameters(originalType, getTypeParameters(type))) return false;

    if (type instanceof PsiCapturedWildcardType) {
      return false;
    }

    if (resolved instanceof PsiMethod) {
      final PsiMethod method = ((PsiMethod)resolved);

      final PsiClass containingClass = method.getContainingClass();
      if (containingClass instanceof PsiAnonymousClass) {
        final HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
        final List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
        if (!superSignatures.isEmpty()) {

          final HierarchicalMethodSignature superSignature = superSignatures.get(0);

          final PsiSubstitutor substitutor = superSignature.getSubstitutor();
          if (!substitutor.getSubstitutionMap().isEmpty()) {
            final PsiMethod superMethod = superSignature.getMethod();

            final PsiType superReturnType = superMethod.getReturnType();
            if (superReturnType instanceof PsiClassType) {
              final PsiClass resolvedClass = ((PsiClassType)superReturnType).resolve();
              if (resolvedClass instanceof PsiTypeParameter) {
                final PsiType expectedReturnType = substitutor.substitute((PsiTypeParameter)resolvedClass);
                if (Comparing.equal(expectedReturnType, method.getReturnType())) {
                  final PsiClassType baseClassType = ((PsiAnonymousClass)containingClass).getBaseClassType();
                  final PsiClassType.ClassResolveResult result = baseClassType.resolveGenerics();
                  final PsiClass anonymousBaseClass = result.getElement();

                  final PsiSubstitutor superHierarchySubstitutor = TypeConversionUtil
                    .getClassSubstitutor(superMethod.getContainingClass(), anonymousBaseClass, PsiSubstitutor.EMPTY);
                  final PsiType maybeTypeParameter = superHierarchySubstitutor.substitute((PsiTypeParameter)resolvedClass);

                  if (maybeTypeParameter instanceof PsiClassType &&
                      ((PsiClassType)maybeTypeParameter).resolve() instanceof PsiTypeParameter) {
                    final PsiSubstitutor newSubstitutor = result.getSubstitutor().put(
                      (PsiTypeParameter)((PsiClassType)maybeTypeParameter).resolve(), type);
                    addRoot(new TypeMigrationUsageInfo(((PsiAnonymousClass)containingClass).getBaseClassReference().getParameterList()),
                            new PsiImmediateClassType(anonymousBaseClass, newSubstitutor),
                            place,
                            alreadyProcessed);
                  }
                }
              }
            }
          }
        }
      }


      final PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
      final OverriderUsageInfo[] overriders = new OverriderUsageInfo[methods.length];
      for (int i = -1; i < methods.length; i++) {
        final TypeMigrationUsageInfo m;
        if (i < 0) {
          final OverriddenUsageInfo overriddenUsageInfo = new OverriddenUsageInfo(method);
          m = overriddenUsageInfo;
          final String newMethodName = isMethodNameCanBeChanged(method);
          if (newMethodName != null) {
            final MigrateGetterNameSetting migrateGetterNameSetting = myRules.getConversionSettings(MigrateGetterNameSetting.class);
            migrateGetterNameSetting.askUserIfNeeded(overriddenUsageInfo, newMethodName, myTypeEvaluator.getType(myCurrentRoot));
          }
        }
        else {
          overriders[i] = new OverriderUsageInfo(methods[i], method);
          m = overriders[i];
        }

        alreadyProcessed = addRoot(m, type, place, alreadyProcessed);
      }

      return !alreadyProcessed;
    }
    else if (resolved instanceof PsiParameter && ((PsiParameter)resolved).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)((PsiParameter)resolved).getDeclarationScope();

      final int index = method.getParameterList().getParameterIndex(((PsiParameter)resolved));
      final PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);

      final OverriderUsageInfo[] overriders = new OverriderUsageInfo[methods.length];
      final OverriddenUsageInfo overriddenUsageInfo = new OverriddenUsageInfo(method.getParameterList().getParameters()[index]);
      for (int i = -1; i < methods.length; i++) {
        final PsiMethod m = i < 0 ? method : methods[i];
        final PsiParameter p = m.getParameterList().getParameters()[index];
        final TypeMigrationUsageInfo paramUsageInfo;
        if (i < 0) {
          paramUsageInfo = overriddenUsageInfo;
        }
        else {
          overriders[i] = new OverriderUsageInfo(p, method);
          paramUsageInfo = overriders[i];
        }
        alreadyProcessed = addRoot(paramUsageInfo, type, place, alreadyProcessed);
      }

      return !alreadyProcessed;
    }
    else {
      return !addRoot(new TypeMigrationUsageInfo(resolved), type, place, alreadyProcessed);
    }
  }

  @NotNull
  private static Set<PsiTypeParameter> getTypeParameters(@NotNull PsiType type) {
    if (type instanceof PsiClassType) {
      PsiTypesUtil.TypeParameterSearcher searcher = new PsiTypesUtil.TypeParameterSearcher();
      type.accept(searcher);
      return searcher.getTypeParameters();
    }
    return Collections.emptySet();
  }

  @Nullable
  private String isMethodNameCanBeChanged(PsiMethod method) {
    if (myCurrentRoot == null) {
      return null;
    }
    final PsiElement root = myCurrentRoot.getElement();
    if (!(root instanceof PsiField)) {
      return null;
    }
    PsiField field = (PsiField) root;
    final PsiType migrationType = myTypeEvaluator.getType(root);
    if (migrationType == null) {
      return null;
    }
    final PsiType sourceType = field.getType();
    if (TypeConversionUtil.isAssignable(migrationType, sourceType)) {
      return null;
    }
    if (!(migrationType.equals(PsiType.BOOLEAN) || migrationType.equals(PsiType.BOOLEAN.getBoxedType(field))) &&
        !(sourceType.equals(PsiType.BOOLEAN) || sourceType.equals(PsiType.BOOLEAN.getBoxedType(field)))) {
      return null;
    }
    final PsiMethod[] getters =
      GetterSetterPrototypeProvider.findGetters(field.getContainingClass(), field.getName(), field.hasModifierProperty(PsiModifier.STATIC));
    if (getters != null) {
      for (PsiMethod getter : getters) {
        if (getter.isEquivalentTo(method)) {
          final String suggestedName = GenerateMembersUtil.suggestGetterName(field.getName(), migrationType, method.getProject());
          if (!suggestedName.equals(method.getName())) {
            if (getter.getContainingClass().findMethodsByName(suggestedName, true).length != 0) {
              return null;
            }
            return suggestedName;
          }
          return null;
        }
      }
    }
    return null;
  }

  private boolean typeContainsTypeParameters(@Nullable PsiType type, @NotNull Set<PsiTypeParameter> excluded) {
    if (!(type instanceof PsiClassType)) return false;
    PsiTypesUtil.TypeParameterSearcher searcher = new PsiTypesUtil.TypeParameterSearcher();
    type.accept(searcher);
    for (PsiTypeParameter parameter : searcher.getTypeParameters()) {
      if (!excluded.contains(parameter) && !myDisappearedTypeParameters.contains(parameter)) {
        return true;
      }
    }
    return false;
  }


  @Nullable
  public static PsiType getElementType(final PsiElement resolved) {
    if (resolved instanceof PsiVariable) {
      return ((PsiVariable)resolved).getType();
    }
    else {
      if (resolved instanceof PsiMethod) {
        return (((PsiMethod)resolved).getReturnType());
      }
      else if (resolved instanceof PsiExpression){
        return (((PsiExpression)resolved).getType());
      } else if (resolved instanceof PsiReferenceParameterList) {
        PsiElement parent = resolved.getParent();
        while (parent != null) {
          LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement);
          final PsiClass psiClass = (PsiClass)((PsiJavaCodeReferenceElement)parent).resolve();
          final PsiClass containingClass = PsiTreeUtil.getParentOfType(parent, PsiClass.class);
          if (psiClass != null && containingClass != null) {
           final PsiSubstitutor classSubstitutor = TypeConversionUtil.getClassSubstitutor(psiClass, containingClass, PsiSubstitutor.EMPTY);
           if (classSubstitutor != null) {
             return JavaPsiFacade.getElementFactory(parent.getProject()).createType(psiClass, classSubstitutor);
           }
          }
          parent = PsiTreeUtil.getParentOfType(parent, PsiJavaCodeReferenceElement.class, true);
        }
      } else if (resolved instanceof PsiClass) {
        return JavaPsiFacade.getElementFactory(resolved.getProject()).createType((PsiClass)resolved, PsiSubstitutor.EMPTY);
      }
    }
    return null;
  }

  public void clearStopException() {
    myException = null;
  }

  boolean addRoot(final TypeMigrationUsageInfo usageInfo, final PsiType type, final PsiElement place, boolean alreadyProcessed) {
    if (myShowWarning && myMigrationRoots.size() > 10 && !ApplicationManager.getApplication().isUnitTestMode()) {
      myShowWarning = false;
      myDialogSemaphore.down();
      try {
        final Runnable checkTimeToStopRunnable = () -> {
          if (Messages.showYesNoCancelDialog(JavaRefactoringBundle.message("type.migration.preview.warning.text"), JavaRefactoringBundle.message("type.migration.action.name"),
                                             Messages.getWarningIcon()) == Messages.YES) {
            myException = new MigrateException();
          }
          myDialogSemaphore.up();
        };
        SwingUtilities.invokeLater(checkTimeToStopRunnable);
      }
      catch (Exception e) {
        //do nothing
      }
    }
    checkInterrupted();
    rememberRootTrace(usageInfo, type, place, alreadyProcessed);
    if (!alreadyProcessed && !(usageInfo.getElement() instanceof PsiExpression) && !getTypeEvaluator().setType(usageInfo, type)) {
      alreadyProcessed = true;
    }

    if (!alreadyProcessed) myMigrationRoots.addFirst(Pair.create(usageInfo, type));
    return alreadyProcessed;
  }

  private void checkInterrupted() {
    if (myException != null) throw myException;
  }

  private void rememberRootTrace(final TypeMigrationUsageInfo usageInfo, final PsiType type, final PsiElement place, final boolean alreadyProcessed) {
    if (myCurrentRoot != null) {
      if (!alreadyProcessed) {
        myProcessedRoots.add(usageInfo);
      }

      if (myProcessedRoots.contains(usageInfo)) {
        HashSet<Pair<TypeMigrationUsageInfo, PsiType>> infos = myRootsTree.get(myCurrentRoot);
        if (infos == null) {
          infos = new HashSet<>();
          myRootsTree.put(myCurrentRoot, infos);
        }
        infos.add(Pair.create(usageInfo, type));
      }
      if (!(usageInfo instanceof OverriderUsageInfo)) { //hide the same usage for all overriders
        setTypeUsage(usageInfo, place);
      }
    }
  }

  private void setTypeUsage(final TypeMigrationUsageInfo usageInfo, final PsiElement place) {
    if (place != null) {
      final Pair<TypeMigrationUsageInfo, TypeMigrationUsageInfo> rooted = Pair.create(usageInfo, myCurrentRoot);
      Set<PsiElement> usages = myRootUsagesTree.get(rooted);
      if (usages == null) {
        usages = new HashSet<>();
        myRootUsagesTree.put(rooted, usages);
      }
      usages.add(place);
    }
  }

  public void setTypeUsage(final PsiElement element, final PsiElement place) {
    setTypeUsage(new TypeMigrationUsageInfo(element), place);
  }

  void markFailedConversion(final Pair<PsiType, PsiType> typePair, final PsiExpression expression) {
    LOG.assertTrue(typePair.getSecond() != null);
    final Pair<SmartPsiElementPointer<PsiExpression>, PsiType> key =
      Pair.create(SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression), typePair.getSecond());
    if (!myFailedConversions.containsKey(key)) {
      myFailedConversions.put(key, getCurrentRoot());
    }
  }

  void setConversionMapping(final PsiExpression expression, final Object obj) {
    if (myConversions.get(expression) != null) {
      return;
    }

    if (obj instanceof TypeConversionDescriptorBase) {
      ((TypeConversionDescriptorBase)obj).setRoot(myCurrentRoot);
    }
    myConversions.put(expression, obj);
  }

  public PsiReference[] markRootUsages(final PsiElement element, final PsiType migrationType) {
    return markRootUsages(element, migrationType, ReferencesSearch.search(element, myRules.getSearchScope(), false).toArray(PsiReference.EMPTY_ARRAY));
  }

  PsiReference[] markRootUsages(final PsiElement element, final PsiType migrationType, final PsiReference[] refs) {
    final List<PsiReference> validReferences = new ArrayList<>();
    for (PsiReference ref1 : refs) {
      final PsiElement ref = ref1.getElement();

      if (element instanceof PsiMethod) {
        final PsiElement parent = Util.getEssentialParent(ref);

        if (!(parent instanceof PsiMethodCallExpression)) {
          continue;
        }

        getTypeEvaluator().setType(new TypeMigrationUsageInfo(parent), migrationType);
      }
      else if (element instanceof PsiVariable) {
        if (ref instanceof PsiReferenceExpression) {
          getTypeEvaluator().setType(new TypeMigrationUsageInfo(ref), PsiUtil.captureToplevelWildcards(migrationType, ref));
        }
      }
      else {
        LOG.error("Method call expression or reference expression expected but found " + element.getClass().getName());
        continue;
      }
      validReferences.add(ref1);
    }

    validReferences.sort(Comparator.comparingInt(o -> o.getElement().getTextOffset()));

    return validReferences.toArray(PsiReference.EMPTY_ARRAY);
  }

  public void setRootAndMigrate(final TypeMigrationUsageInfo newRootUsageInfo, final PsiType migrationType, final PsiReference[] usages) {
    final TypeMigrationUsageInfo oldRoot = getCurrentRoot();
    myCurrentRoot = newRootUsageInfo;
    PsiElement root = newRootUsageInfo.getElement();
    if (root instanceof PsiMethod) {
      migrateMethodReturnExpression(migrationType, (PsiMethod)root);
    }
    else if (root instanceof PsiParameter && ((PsiParameter)root).getDeclarationScope() instanceof PsiMethod) {
      migrateMethodCallExpressions(migrationType, (PsiParameter)root, null);
    }
    else if (root instanceof PsiVariable || root instanceof PsiExpression) {
      final PsiElement element = getContainingStatement(root);
      if (root instanceof PsiExpression) {
        migrateExpressionType((PsiExpression)root, migrationType, element, false, true);
        myTypeEvaluator.setType(newRootUsageInfo, migrationType);
      }
      element.accept(new TypeMigrationStatementProcessor(element, this));
    }
    else if (root instanceof PsiReferenceParameterList) {
      final TypeMigrationUsageInfo info = new TypeMigrationUsageInfo(root);
      info.setOwnerRoot(oldRoot);
      myClassTypeArgumentsChange.put(info, (PsiClassType)migrationType);
      new ClassTypeArgumentMigrationProcessor(this).migrateClassTypeParameter((PsiReferenceParameterList)root, (PsiClassType)migrationType);
    }

    final Set<PsiElement> processed = new HashSet<>();
    for (PsiReference usage : usages) {
      migrateRootUsageExpression(usage, processed);
    }
  }

  private static PsiElement getContainingStatement(final PsiElement root) {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(root, PsiStatement.class);
    PsiExpression condition = getContainingCondition(root, statement);
    if (condition != null) return condition;
    final PsiField field = PsiTreeUtil.getParentOfType(root, PsiField.class);
    return statement != null ? statement : field != null ? field : root;
  }

  private static PsiExpression getContainingCondition(PsiElement root, PsiStatement statement) {
    PsiExpression condition = null;
    if (statement instanceof PsiConditionalLoopStatement) {
      condition = ((PsiConditionalLoopStatement)statement).getCondition();
    }
    else if (statement instanceof PsiIfStatement) {
      condition = ((PsiIfStatement)statement).getCondition();
    }
    return PsiTreeUtil.isAncestor(condition, root, false) ? condition : null;
  }

  void migrateRootUsageExpression(final PsiReference usage, final Set<? super PsiElement> processed) {
    final PsiElement ref = usage.getElement();
    if (ref.getLanguage() == JavaLanguage.INSTANCE) {
      final PsiElement element = getContainingStatement(ref);
      if (element != null && !processed.contains(element)) {
        processed.add(element);
        element.accept(new TypeMigrationStatementProcessor(ref, this));
      }
    }
  }

  void migrateMethodCallExpressions(final PsiType migrationType, final PsiParameter param, final PsiClass psiClass) {
    boolean checkNumberOfArguments = false;
    if (param.getType() instanceof PsiEllipsisType && !(migrationType instanceof PsiEllipsisType)) {
      checkNumberOfArguments = true;
    }
    final PsiType strippedType =
                  migrationType instanceof PsiEllipsisType ? ((PsiEllipsisType)migrationType).getComponentType() : migrationType;
    final PsiMethod method = (PsiMethod)param.getDeclarationScope();
    final PsiParameterList parameterList = method.getParameterList();
    final int parametersCount = parameterList.getParametersCount();
    final int index = parameterList.getParameterIndex(param);
    final List<PsiReference> refs = filterReferences(psiClass, ReferencesSearch.search(method, method.getUseScope().intersectWith(myRules.getSearchScope()), false));
    for (PsiReference ref1 : refs) {
      final PsiElement ref = ref1.getElement();
      final PsiElement parent = Util.getEssentialParent(ref);
      if (parent instanceof PsiCallExpression) {
        final PsiExpressionList argumentList = ((PsiCallExpression)parent).getArgumentList();
        if (argumentList != null) {
          final PsiExpression[] expressions = argumentList.getExpressions();
          if (checkNumberOfArguments && parametersCount != expressions.length) {
            markFailedConversion(Pair.create(param.getType(), migrationType), (PsiCallExpression)parent);
          }
          if (index > -1 && index < expressions.length) {
            for (int idx = index; idx < (param.isVarArgs() ? expressions.length : index + 1); idx++) {
              final PsiExpression actual = expressions[idx];
              final PsiType type = getTypeEvaluator().evaluateType(actual);
              if (type != null) {
                migrateExpressionType(actual, strippedType, parent, TypeConversionUtil.isAssignable(strippedType, type), true);
              }
            }
          }
        }
      } else if (ref instanceof PsiDocTagValue) {
        myConversions.put(ref, method);
      }
    }
  }

  private void migrateMethodReturnExpression(final PsiType migrationType, final PsiMethod method) {
    final PsiCodeBlock block = method.getBody();
    if (block != null) {
      block.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
          final PsiExpression value = statement.getReturnValue();
          if (value != null) {
            final PsiType type = getTypeEvaluator().evaluateType(value);
            if (type != null && !type.equals(migrationType)) {
              migrateExpressionType(value, migrationType, statement, TypeConversionUtil.isAssignable(migrationType, type), true);
            }
          }
        }

        @Override
        public void visitClass(PsiClass aClass) {}

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {}
      });
    }
  }

  private void iterate() {
    final List<Pair<TypeMigrationUsageInfo, PsiType>> roots = new ArrayList<>(myMigrationRoots);

    myMigrationRoots = new LinkedList<>();

    final PsiReference[][] cachedUsages = new PsiReference[roots.size()][];
    int j = 0;

    for (final Pair<TypeMigrationUsageInfo, PsiType> p : roots) {
      cachedUsages[j++] = markRootUsages(p.getFirst().getElement(), p.getSecond());
    }

    j = 0;

    for (final Pair<TypeMigrationUsageInfo, PsiType> root : roots) {
      setRootAndMigrate(root.getFirst(), root.getSecond(), cachedUsages[j++]);
    }
  }

  private void migrate(boolean autoMigrate, final PsiElement... victims) {

    myMigrationRoots = new LinkedList<>();
    myTypeEvaluator = new TypeEvaluator(myMigrationRoots, this, myProject);

    SmartTypePointerManager smartTypePointerManager = SmartTypePointerManager.getInstance(myProject);
    for (PsiElement victim : victims) {
      // use deeply immediate types
      PsiType migrationType = smartTypePointerManager.createSmartTypePointer(myMigrationRootTypeFunction.fun(victim)).getType();
      addMigrationRoot(victim, migrationType, null, false, true, true);
    }

    if (autoMigrate) {
      while (!myMigrationRoots.isEmpty()) {
        iterate();
      }
    }

    myDialogSemaphore.waitFor();
    checkInterrupted();
  }

  public TypeEvaluator getTypeEvaluator() {
    return myTypeEvaluator;
  }

  public Map<TypeMigrationUsageInfo, HashSet<Pair<TypeMigrationUsageInfo, PsiType>>> getRootsTree() {
    return myRootsTree;
  }

  TypeMigrationUsageInfo getCurrentRoot() {
    return myCurrentRoot;
  }

  public LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> getMigrationRoots() {
    return myMigrationRoots;
  }

  public static List<PsiReference> filterReferences(final PsiClass psiClass, final Query<? extends PsiReference> memberReferences) {
    final List<PsiReference> refs = new ArrayList<>();
    for (PsiReference memberReference : memberReferences) {
      if (psiClass == null) {
        refs.add(memberReference);
      } else {
        final PsiElement referencedElement = memberReference.getElement();
        if (referencedElement instanceof PsiReferenceExpression) {
          final PsiExpression qualifierExpression = ((PsiReferenceExpression)referencedElement).getQualifierExpression();
          if (qualifierExpression != null) {
            final PsiType qualifierType = qualifierExpression.getType();
            if (qualifierType instanceof PsiClassType && psiClass == ((PsiClassType)qualifierType).resolve()) {
              refs.add(memberReference);
            }
          } else {
            if (psiClass == PsiTreeUtil.getParentOfType(referencedElement, PsiClass.class)) {
              refs.add(memberReference);
            }
          }
        }
      }
    }
    return refs;
  }

  private static boolean canBeRoot(@Nullable PsiElement element, @NotNull SearchScope migrationScope) {
    if (element == null) return false;
    return element.isValid() &&
           element.isPhysical() &&
           PsiSearchScopeUtil.isInScope(migrationScope, element);
  }

  @TestOnly
  public String getMigrationReport() {
    final StringBuilder buffer = new StringBuilder();

    buffer.append("Types:\n").append(getTypeEvaluator().getReport()).append("\n");

    buffer.append("Conversions:\n");

    final String[] conversions = new String[myConversions.size()];
    int k = 0;

    for (final PsiElement expr : myConversions.keySet()) {
      final Object conversion = myConversions.get(expr);

      if (conversion instanceof Pair && ((Pair)conversion).first == null) {
        conversions[k++] = (expr.getText() + " -> " + ((Pair)conversion).second + "\n");
      } else {
        conversions[k++] = (expr.getText() + " -> " + conversion + "\n");
      }
    }

    Arrays.sort(conversions);

    for (String conversion : conversions) {
      buffer.append(conversion);
    }

    buffer.append("\nNew expression type changes:\n");

    final String[] newChanges = new String[myNewExpressionTypeChange.size()];
    k = 0;

    for (final Map.Entry<TypeMigrationUsageInfo, PsiType> entry : myNewExpressionTypeChange.entrySet()) {
      final PsiElement element = entry.getKey().getElement();
      newChanges[k++] = (element != null ? element.getText() : entry.getKey()) + " -> " + entry.getValue().getCanonicalText() + "\n";
    }

    Arrays.sort(newChanges);

    for (String change : newChanges) {
      buffer.append(change);
    }

    buffer.append("Fails:\n");

    final ArrayList<Pair<SmartPsiElementPointer<PsiExpression>, PsiType>>
      failsList = new ArrayList<>(myFailedConversions.keySet());
    failsList.sort((o1, o2) -> {
      final PsiElement element1 = o1.getFirst().getElement();
      final PsiElement element2 = o2.getFirst().getElement();
      if (element1 == null || element2 == null) return 0;
      return element1.getText().compareTo(element2.getText());
    });

    for (final Pair<SmartPsiElementPointer<PsiExpression>, PsiType> p : failsList) {
      final PsiElement element = p.getFirst().getElement();
      if (element != null) {
        buffer.append(element.getText()).append("->").append(p.getSecond().getCanonicalText()).append("\n");
      }
    }

    return buffer.toString();
  }

  public static class MigrateException extends RuntimeException { }
}
