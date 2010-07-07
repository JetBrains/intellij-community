package com.intellij.refactoring.typeMigration;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.usageInfo.OverridenUsageInfo;
import com.intellij.refactoring.typeMigration.usageInfo.OverriderUsageInfo;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Sep 19, 2004
 * Time: 6:13:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class TypeMigrationLabeler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.typeMigration.TypeMigrationLabeler");
  private boolean myShowWarning = true;

  public TypeMigrationRules getRules() {
    return myRules;
  }

  private final TypeMigrationRules myRules;
  private TypeEvaluator myTypeEvaluator;
  private final LinkedHashMap<PsiElement, Object> myConversions;
  private final HashSet<Pair<PsiAnchor, PsiType>> myFailedConversions;
  private LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> myMigrationRoots;
  private final LinkedHashMap<TypeMigrationUsageInfo, PsiType> myNewExpressionTypeChange;
  private final LinkedHashMap<TypeMigrationUsageInfo, PsiClassType> myClassTypeArgumentsChange;

  private TypeMigrationUsageInfo[] myMigratedUsages = null;

  private TypeMigrationUsageInfo myCurrentRoot;
  private final Map<TypeMigrationUsageInfo, HashSet<Pair<TypeMigrationUsageInfo, PsiType>>> myRootsTree =
      new HashMap<TypeMigrationUsageInfo, HashSet<Pair<TypeMigrationUsageInfo, PsiType>>>();
  private final Map<Pair<TypeMigrationUsageInfo, TypeMigrationUsageInfo>, Set<PsiElement>> myRootUsagesTree = new HashMap<Pair<TypeMigrationUsageInfo, TypeMigrationUsageInfo>, Set<PsiElement>>();
  private final Set<TypeMigrationUsageInfo> myProcessedRoots = new HashSet<TypeMigrationUsageInfo>();


  public TypeMigrationLabeler(final TypeMigrationRules rules) {
    myRules = rules;
    
    myConversions = new LinkedHashMap<PsiElement, Object>();
    myFailedConversions = new HashSet<Pair<PsiAnchor, PsiType>>();
    myNewExpressionTypeChange = new LinkedHashMap<TypeMigrationUsageInfo, PsiType>();
    myClassTypeArgumentsChange = new LinkedHashMap<TypeMigrationUsageInfo, PsiClassType>();
  }

  public boolean hasFailedConversions() {
    return myFailedConversions.size() > 0;
  }

  public String[] getFailedConversionsReport() {
    final String[] report = new String[myFailedConversions.size()];
    int j = 0;

    for (final Pair<PsiAnchor, PsiType> p : myFailedConversions) {
      final PsiElement element = p.getFirst().retrieve();

      report[j++] = "Cannot convert type of expression <b>" +
                    StringUtil.escapeXml(element.getText()) +
                    "</b>" +
                    " from <b>" +
                    StringUtil.escapeXml(((PsiExpression)element).getType().getCanonicalText()) +
                    "</b> to <b>" + StringUtil.escapeXml(p.getSecond().getCanonicalText()) +
                    "</b><br>";
    }

    return report;
  }

  public UsageInfo[] getFailedUsages() {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>(myFailedConversions.size());
    for (final Pair<PsiAnchor, PsiType> p : myFailedConversions) {
      final PsiExpression expr = (PsiExpression)p.getFirst().retrieve();
      if (expr != null) {
        usages.add(new UsageInfo(expr) {
          public String getTooltipText() {
            return "Cannot convert type of the expression from " +
                   expr.getType().getCanonicalText() +
                   " to " +
                   p.getSecond().getCanonicalText();
          }
        });
      }
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  public TypeMigrationUsageInfo[] getMigratedUsages() {
    final LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> declarations = getTypeEvaluator().getMigratedDeclarations();
    final TypeMigrationUsageInfo[] usages = new TypeMigrationUsageInfo[declarations.size() + myConversions.size() + myNewExpressionTypeChange.size() + myClassTypeArgumentsChange.size()];

    int j = 0;

    List<PsiElement> conversionExprs = new ArrayList<PsiElement>(myConversions.keySet());
    Collections.sort(conversionExprs, new Comparator<PsiElement>() {
      public int compare(final PsiElement e1, final PsiElement e2) {
        return e2.getTextRange().getStartOffset() - e1.getTextRange().getStartOffset();
      }
    });
    for (final PsiElement element : conversionExprs) {
      final Object conv = myConversions.get(element);

      usages[j++] = new TypeMigrationUsageInfo(element) {
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
      };
    }

    for (final TypeMigrationUsageInfo expr : myNewExpressionTypeChange.keySet()) {

      usages[j++] = expr;
    }

    for (final Pair<TypeMigrationUsageInfo, PsiType> p : declarations) {
      final TypeMigrationUsageInfo element = p.getFirst();
      usages[j++] = element;
    }

    for (TypeMigrationUsageInfo info : myClassTypeArgumentsChange.keySet()) {
      usages[j++] = info;
    }
    return usages;
  }

  public void change(final TypeMigrationUsageInfo usageInfo) {
    final PsiElement element = usageInfo.getElement();
    if (element == null) return;
    final Project project = element.getProject();
    if (element instanceof PsiExpression) {
      final PsiExpression expression = (PsiExpression)element;
      if (element instanceof PsiNewExpression) {
        for (Map.Entry<TypeMigrationUsageInfo, PsiType> info : myNewExpressionTypeChange.entrySet()) {
          final PsiElement expressionToReplace = info.getKey().getElement();
          if (expression.equals(expressionToReplace)) {
            TypeMigrationReplacementUtil.replaceNewExpressionType(project, (PsiNewExpression)expressionToReplace, info);
          }
        }
      }
      final Object conversion = myConversions.get(element);
      if (conversion != null) {
        myConversions.remove(element);
        TypeMigrationReplacementUtil.replaceExpression(expression, project, conversion);
      }
    } else if (element instanceof PsiReferenceParameterList) {
      for (Map.Entry<TypeMigrationUsageInfo, PsiClassType> entry : myClassTypeArgumentsChange.entrySet()) {
        if (element.equals(entry.getKey().getElement())) { //todo check null
          final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
          try {
            element.getParent().replace(factory.createReferenceElementByType(entry.getValue()));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }
    else {
      TypeMigrationReplacementUtil.migratePsiMemeberType(element, project, getTypeEvaluator().getType(usageInfo));
    }
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
      markFailedConversion(new Pair<PsiType, PsiType>(fromType, toType), expr);
    }
    else {
      setConversionMapping(expr, conversion);
    }
  }

  public void migrateExpressionType(final PsiExpression expr, final PsiType migrationType, final PsiElement place, boolean alreadyProcessed, final boolean isCovariant) {
    PsiType originalType = expr.getType();

    LOG.assertTrue(originalType != null);

    if (originalType.equals(migrationType)) return;

    if (originalType.equals(PsiType.NULL)) {
      if (migrationType instanceof PsiPrimitiveType) {
        markFailedConversion(new Pair<PsiType, PsiType>(originalType, migrationType), expr);
      }
      return;
    }

    if (expr instanceof PsiConditionalExpression) {

    } else if (expr instanceof PsiClassObjectAccessExpression) {
      if (!TypeConversionUtil.isAssignable(migrationType, expr.getType())) {
        markFailedConversion(new Pair<PsiType, PsiType>(expr.getType(), migrationType), expr);
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
          final PsiType elemenType = ((PsiArrayType)migrationType).getComponentType();

          final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)expr).getArrayInitializer();

          if (arrayInitializer != null) {
            final PsiExpression[] initializers = arrayInitializer.getInitializers();
            for (int i = initializers.length - 1; i >= 0; i--) {
              migrateExpressionType(initializers[i], elemenType, place, alreadyProcessed, true);
            }
          }

          if (isGenericsArrayType(elemenType)){
            markFailedConversion(new Pair<PsiType, PsiType>(originalType, migrationType), expr);
            return;
          }

          myNewExpressionTypeChange.put(new TypeMigrationUsageInfo(expr), migrationType);
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
              myNewExpressionTypeChange.put(new TypeMigrationUsageInfo(expr), type);
              getTypeEvaluator().setType(new TypeMigrationUsageInfo(expr), type);
              return;
            }
          }
        }
      }

    }
    
    convertExpression(expr, migrationType, originalType, isCovariant);
  }

  private static boolean isGenericsArrayType(final PsiType elemenType) {
    if (elemenType instanceof PsiClassType && ((PsiClassType)elemenType).hasParameters()) {
      return true;
    } else if (elemenType instanceof PsiArrayType) {
      final PsiType componentType = ((PsiArrayType)elemenType).getComponentType();
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
    if (type.equals(PsiType.NULL)) {
      return false;
    }

    final PsiElement resolved = Util.normalizeElement(element);

    final SearchScope searchScope = myRules.getSearchScope();
    if (!resolved.isPhysical() || !PsiSearchScopeUtil.isInScope(searchScope, resolved)) {
      return false;
    }

    final PsiType originalType = getElementType(resolved);

    LOG.assertTrue(originalType != null);

    type = userDefinedType ? type : TypeEvaluator.substituteType(type, originalType, isContraVariantPosition);

    if (!userDefinedType) {
      if (typeContainsTypeParameters(originalType)) return false;
    }

    if (type instanceof PsiCapturedWildcardType) {
      return false;
    }

    if (resolved instanceof PsiMethod) {
      final PsiMethod method = ((PsiMethod)resolved);
      final PsiMethod[] methods = OverridingMethodsSearch.search(method, method.getUseScope(), false).toArray(PsiMethod.EMPTY_ARRAY);
      final OverridenUsageInfo overridenUsageInfo = new OverridenUsageInfo(method);
      final OverriderUsageInfo[] overriders = new OverriderUsageInfo[methods.length];
      for (int i = -1; i < methods.length; i++) {
        final TypeMigrationUsageInfo m;
        if (i < 0) {
          m = overridenUsageInfo;
        }
        else {
          overriders[i] = new OverriderUsageInfo(methods[i], method);
          m = overriders[i];
        }

        alreadyProcessed = addRoot(m, type, place, alreadyProcessed);
      }
      overridenUsageInfo.setOverriders(overriders);

      return !alreadyProcessed;
    }
    else if (resolved instanceof PsiParameter && ((PsiParameter)resolved).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(resolved, PsiMethod.class);

      if (method == null) {
        return false;
      }

      final int index = method.getParameterList().getParameterIndex(((PsiParameter)resolved));
      final PsiMethod[] methods = OverridingMethodsSearch.search(method, method.getUseScope(), false).toArray(PsiMethod.EMPTY_ARRAY);

      final OverriderUsageInfo[] overriders = new OverriderUsageInfo[methods.length];
      final OverridenUsageInfo overridenUsageInfo = new OverridenUsageInfo(method.getParameterList().getParameters()[index]);
      for (int i = -1; i < methods.length; i++) {
        final PsiMethod m = i < 0 ? method : methods[i];
        final PsiParameter p = m.getParameterList().getParameters()[index];
        final TypeMigrationUsageInfo paramUsageInfo;
        if (i < 0) {
          paramUsageInfo = overridenUsageInfo;
        }
        else {
          overriders[i] = new OverriderUsageInfo(p, method);
          paramUsageInfo = overriders[i];
        }
        alreadyProcessed = addRoot(paramUsageInfo, type, place, alreadyProcessed);
      }

      overridenUsageInfo.setOverriders(overriders);

      return !alreadyProcessed;
    }
    else {
      return !addRoot(new TypeMigrationUsageInfo(resolved), type, place, alreadyProcessed);
    }
  }

  static boolean typeContainsTypeParameters(PsiType originalType) {
    if (originalType instanceof PsiClassType) {
      final PsiClassType psiClassType = (PsiClassType)originalType;
      if (psiClassType.resolve() instanceof PsiTypeParameter) {
        return true;
      }
      for (PsiType paramType : psiClassType.getParameters()) {
        if (paramType instanceof PsiClassType && ((PsiClassType)paramType).resolve() instanceof PsiTypeParameter) return true;
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
        final PsiElement parent = resolved.getParent();
        LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement);
        final PsiClass psiClass = (PsiClass)((PsiJavaCodeReferenceElement)parent).resolve();
        return JavaPsiFacade.getElementFactory(parent.getProject()).createType(psiClass, TypeConversionUtil.getSuperClassSubstitutor(psiClass, PsiTreeUtil.getParentOfType(parent,
                                                                                                                                     PsiClass.class),
                                                                                                         PsiSubstitutor.EMPTY));
      } else if (resolved instanceof PsiClass) {
        return JavaPsiFacade.getElementFactory(resolved.getProject()).createType((PsiClass)resolved, PsiSubstitutor.EMPTY);
      }
    }
    LOG.error("should not happen: " + resolved.getClass());
    return null;
  }

  boolean addRoot(final TypeMigrationUsageInfo usageInfo, final PsiType type, final PsiElement place, boolean alreadyProcessed) {
    if (myShowWarning && myMigrationRoots.size() > 10 && !ApplicationManager.getApplication().isUnitTestMode()) {
      final MigrateException[] ex = new MigrateException[1];
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            if (Messages.showYesNoCancelDialog("Found more than 10 roots to migrate. Do you want to preview?", "Type Migration", Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE) {
              ex[0] = new MigrateException();
            }
          }
        });
      }
      catch (Exception e) {
        //do nothing
      }
      if (ex[0] != null) throw ex[0];
      myShowWarning = false;
    }
    rememberRootTrace(usageInfo, type, place, alreadyProcessed);
    if (!alreadyProcessed && !getTypeEvaluator().setType(usageInfo, type)) {
      alreadyProcessed = true;
    }

    if (!alreadyProcessed) myMigrationRoots.addFirst(new Pair<TypeMigrationUsageInfo, PsiType>(usageInfo, type));
    return alreadyProcessed;
  }

  private void rememberRootTrace(final TypeMigrationUsageInfo usageInfo, final PsiType type, final PsiElement place, final boolean alreadyProcessed) {
    if (myCurrentRoot != null) {
      if (!alreadyProcessed) {
        myProcessedRoots.add(usageInfo);
      }

      if (myProcessedRoots.contains(usageInfo)) {
        HashSet<Pair<TypeMigrationUsageInfo, PsiType>> infos = myRootsTree.get(myCurrentRoot);
        if (infos == null) {
          infos = new HashSet<Pair<TypeMigrationUsageInfo, PsiType>>();
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
        usages = new HashSet<PsiElement>();
        myRootUsagesTree.put(rooted, usages);
      }
      usages.add(place);
    }
  }
  
  public void setTypeUsage(final PsiElement element, final PsiElement place) {
    setTypeUsage(new TypeMigrationUsageInfo(element), place);
  }

  void markFailedConversion(final Pair<PsiType, PsiType> typePair, final PsiExpression expression) {
    myFailedConversions.add(new Pair<PsiAnchor, PsiType>(PsiAnchor.create(expression), typePair.getSecond()));
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
    return markRootUsages(element, migrationType, ReferencesSearch.search(element, myRules.getSearchScope(), false).toArray(new PsiReference[0]));
  }

  PsiReference[] markRootUsages(final PsiElement element, final PsiType migrationType, final PsiReference[] refs) {
    final List<PsiReference> validReferences = new ArrayList<PsiReference>();
    for (PsiReference ref1 : refs) {
      final PsiElement ref = ref1.getElement();

      if (ref != null) {
        if (element instanceof PsiMethod) {
          final PsiElement parent = Util.getEssentialParent(ref);

          if (!(parent instanceof PsiMethodCallExpression)) {
            continue;
          }

          getTypeEvaluator().setType(new TypeMigrationUsageInfo(parent), migrationType);
        }
        else if (element instanceof PsiVariable) {
          if (ref instanceof PsiReferenceExpression) {
            getTypeEvaluator().setType(new TypeMigrationUsageInfo(ref), PsiImplUtil.normalizeWildcardTypeByPosition(migrationType, (PsiReferenceExpression)ref));
          }
        }
        else {
          LOG.error("Method call expression or reference expression expected but found " + element.getClass().getName());
          continue;
        }
        validReferences.add(ref1);
      }
    }

    Collections.sort(validReferences, new Comparator<PsiReference>() {
      public int compare(final PsiReference o1, final PsiReference o2) {
        return o1.getElement().getTextOffset() - o2.getElement().getTextOffset();
      }
    });

    return validReferences.toArray(new PsiReference[validReferences.size()]);
  }

  public void migrateRoot(final PsiElement root, final PsiType migrationType, final PsiReference[] usages) {
    if (root instanceof PsiMethod) {
      migrateMethodReturnExpression(migrationType, (PsiMethod)root);
    }
    else if (root instanceof PsiParameter && ((PsiParameter)root).getDeclarationScope() instanceof PsiMethod) {
      migrateMethodCallExpressions(migrationType, (PsiParameter)root, null);
    }
    else if (root instanceof PsiVariable || root instanceof PsiExpression){
      final PsiElement element = getContainingStatement(root);
      element.accept(new TypeMigrationStatementProcessor(element, this));
    } else if (root instanceof PsiReferenceParameterList) {
      myClassTypeArgumentsChange.put(new TypeMigrationUsageInfo(root), (PsiClassType)migrationType);
      new ClassTypeArgumentMigrationProcessor(this).migrateClassTypeParameter((PsiReferenceParameterList)root, migrationType);
    }

    final Set<PsiElement> processed = new HashSet<PsiElement>();
    for (PsiReference usage : usages) {
      migrateRootUsageExpression(usage, processed);
    }
  }

  private static PsiElement getContainingStatement(final PsiElement root) {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(root, PsiStatement.class);
    final PsiField field = PsiTreeUtil.getParentOfType(root, PsiField.class);
    return statement != null ? statement : field != null ? field : root;
  }

  void migrateRootUsageExpression(final PsiReference usage, final Set<PsiElement> processed) {
    final PsiElement ref = usage.getElement();
    if (ref != null && ref.getLanguage() == StdLanguages.JAVA) {
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
            markFailedConversion(new Pair<PsiType, PsiType>(param.getType(), migrationType), (PsiCallExpression)parent);
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
      });
    }
  }

  private void iterate() {
    final LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> roots =
        (LinkedList<Pair<TypeMigrationUsageInfo, PsiType>>)myMigrationRoots.clone();

    myMigrationRoots = new LinkedList<Pair<TypeMigrationUsageInfo, PsiType>>();

    final PsiReference[][] cachedUsages = new PsiReference[roots.size()][];
    int j = 0;

    for (final Pair<TypeMigrationUsageInfo, PsiType> p : roots) {
      cachedUsages[j++] = markRootUsages(p.getFirst().getElement(), p.getSecond());
    }

    j = 0;

    for (final Pair<TypeMigrationUsageInfo, PsiType> root : roots) {
      myCurrentRoot = root.getFirst();
      migrateRoot(root.getFirst().getElement(), root.getSecond(), cachedUsages[j++]);
    }
  }

  private void migrate(boolean autoMigrate, final PsiElement... victims) {
    myMigrationRoots = new LinkedList<Pair<TypeMigrationUsageInfo, PsiType>>();
    myTypeEvaluator = new TypeEvaluator(myMigrationRoots, this);


    final PsiType rootType = myRules.getMigrationRootType();
    for (PsiElement victim : victims) {
      addMigrationRoot(victim, rootType, null, false, true, true);
    }

    if (autoMigrate) {
      while (myMigrationRoots.size() > 0) {
        iterate();
      }
    }
  }

  public TypeEvaluator getTypeEvaluator() {
    return myTypeEvaluator;
  }

  public Map<TypeMigrationUsageInfo, HashSet<Pair<TypeMigrationUsageInfo, PsiType>>> getRootsTree() {
    return myRootsTree;
  }

  public void setCurrentRoot(final TypeMigrationUsageInfo currentRoot) {
    myCurrentRoot = currentRoot;
  }

  public LinkedList<Pair<TypeMigrationUsageInfo, PsiType>> getMigrationRoots() {
    return myMigrationRoots;
  }

  public static List<PsiReference> filterReferences(final PsiClass psiClass, final Query<PsiReference> memberReferences) {
    final List<PsiReference> refs = new ArrayList<PsiReference>();
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

  public String getMigrationReport() {
    final StringBuffer buffer = new StringBuffer();

    buffer.append("Types:\n" + getTypeEvaluator().getReport() + "\n");
    buffer.append("Conversions:\n");

    final String[] conversions = new String[myConversions.size()];
    int k = 0;

    for (final PsiElement expr : myConversions.keySet()) {
      final Object conv = myConversions.get(expr);

      if (conv instanceof Pair && ((Pair)conv).first == null) {
        conversions[k++] = (expr.getText() + " -> " + ((Pair)conv).second + "\n");
      } else {
        conversions[k++] = (expr.getText() + " -> " + conv + "\n");
      }
    }

    Arrays.sort(conversions, new Comparator() {
      public int compare(Object x, Object y) {
        return ((String)x).compareTo((String)y);
      }
    });

    for (String conversion : conversions) {
      buffer.append(conversion);
    }

    buffer.append("\nNew expression type changes:\n");

    final String[] newchanges = new String[myNewExpressionTypeChange.size()];
    k = 0;

    for (final Map.Entry<TypeMigrationUsageInfo, PsiType> entry : myNewExpressionTypeChange.entrySet()) {


      newchanges[k++] = entry.getKey().getElement().getText() + " -> " + entry.getValue().getCanonicalText() + "\n";
    }

    Arrays.sort(newchanges, new Comparator() {
      public int compare(Object x, Object y) {
        return ((String)x).compareTo((String)y);
      }
    });

    for (String newchange : newchanges) {
      buffer.append(newchange);
    }

    buffer.append("Fails:\n");

    final ArrayList<Pair<PsiAnchor, PsiType>> failsList = new ArrayList<Pair<PsiAnchor, PsiType>>(myFailedConversions);
    Collections.sort(failsList, new Comparator<Pair<PsiAnchor, PsiType>>() {
      public int compare(final Pair<PsiAnchor, PsiType> o1, final Pair<PsiAnchor, PsiType> o2) {
        final PsiElement element1 = o1.getFirst().retrieve();
        final PsiElement element2 = o2.getFirst().retrieve();
        if (element1 == null || element2 == null) return 0;
        return element1.getText().compareTo(element2.getText());
      }
    });

    for (final Pair<PsiAnchor, PsiType> p : failsList) {
      final PsiElement element = p.getFirst().retrieve();
      if (element != null) {
        buffer.append(element.getText() + "->" + p.getSecond().getCanonicalText() + "\n");
      }
    }

    return buffer.toString();
  }

  public static class MigrateException extends RuntimeException {
  }
}
