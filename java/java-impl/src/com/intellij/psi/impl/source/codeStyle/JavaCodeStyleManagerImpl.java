// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.search.MethodDeepestSuperSearcher;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author max
 */
public class JavaCodeStyleManagerImpl extends JavaCodeStyleManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl");

  @NonNls private static final String IMPL_SUFFIX = "Impl";
  @NonNls private static final String GET_PREFIX = "get";
  @NonNls private static final String IS_PREFIX = "is";
  @NonNls private static final String FIND_PREFIX = "find";
  @NonNls private static final String CREATE_PREFIX = "create";
  @NonNls private static final String SET_PREFIX = "set";
  
  @NonNls private static final String[] ourPrepositions = {
    "as", "at", "by", "down", "for", "from", "in", "into", "of", "on", "onto", "out", "over",
    "per", "to", "up", "upon", "via", "with"};


  private final Project myProject;

  public JavaCodeStyleManagerImpl(final Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public PsiElement shortenClassReferences(@NotNull PsiElement element) throws IncorrectOperationException {
    return shortenClassReferences(element, 0);
  }

  @NotNull
  @Override
  public PsiElement shortenClassReferences(@NotNull PsiElement element, int flags) throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    if (!SourceTreeToPsiMap.hasTreeElement(element)) return element;

    final boolean addImports = !BitUtil.isSet(flags, DO_NOT_ADD_IMPORTS);
    final boolean incompleteCode = BitUtil.isSet(flags, INCOMPLETE_CODE);

    final ReferenceAdjuster adjuster = ReferenceAdjuster.Extension.getReferenceAdjuster(element.getLanguage());
    if (adjuster != null) {
      final ASTNode reference = adjuster.process(element.getNode(), addImports, incompleteCode, myProject);
      return SourceTreeToPsiMap.treeToPsiNotNull(reference);
    }
    else {
      return element;
    }
  }

  @Override
  public void shortenClassReferences(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    if (SourceTreeToPsiMap.hasTreeElement(element)) {
      final ReferenceAdjuster adjuster = ReferenceAdjuster.Extension.getReferenceAdjuster(element.getLanguage());
      if (adjuster != null) {
        adjuster.processRange(element.getNode(), startOffset, endOffset, myProject);
      }
    }
  }

  @NotNull
  @Override
  public PsiElement qualifyClassReferences(@NotNull PsiElement element) {
    final ReferenceAdjuster adjuster = ReferenceAdjuster.Extension.getReferenceAdjuster(element.getLanguage());
    if (adjuster != null) {
      final ASTNode reference = adjuster.process(element.getNode(), false, false, true, true);
      return SourceTreeToPsiMap.treeToPsiNotNull(reference);
    }
    return element;
  }

  @Override
  public void optimizeImports(@NotNull PsiFile file) throws IncorrectOperationException {
    CheckUtil.checkWritable(file);
    if (file instanceof PsiJavaFile) {
      PsiImportList newList = prepareOptimizeImportsResult((PsiJavaFile)file);
      if (newList != null) {
        final PsiImportList importList = ((PsiJavaFile)file).getImportList();
        if (importList != null) {
          importList.replace(newList);
        }
      }
    }
  }

  @Override
  public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file) {
    return new ImportHelper(JavaCodeStyleSettings.getInstance(file)).prepareOptimizeImportsResult(file);
  }

  @Override
  public boolean hasConflictingOnDemandImport(@NotNull PsiJavaFile file, @NotNull PsiClass psiClass, @NotNull String referenceName) {
    return ImportHelper.hasConflictingOnDemandImport(file, psiClass, referenceName);
  }

  @Override
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass) {
    return new ImportHelper(JavaCodeStyleSettings.getInstance(file)).addImport(file, refClass);
  }

  @Override
  public void removeRedundantImports(@NotNull final PsiJavaFile file) throws IncorrectOperationException {
    final Collection<PsiImportStatementBase> redundant = findRedundantImports(file);
    if (redundant == null) return;

    for (final PsiImportStatementBase importStatement : redundant) {
      final PsiJavaCodeReferenceElement ref = importStatement.getImportReference();
      //Do not remove non-resolving refs
      if (ref == null || ref.resolve() == null) {
        continue;
      }

      importStatement.delete();
    }
  }

  @Override
  @Nullable
  public Collection<PsiImportStatementBase> findRedundantImports(@NotNull final PsiJavaFile file) {
    final PsiImportList importList = file.getImportList();
    if (importList == null) return null;
    final PsiImportStatementBase[] imports = importList.getAllImportStatements();
    if( imports.length == 0 ) return null;

    Set<PsiImportStatementBase> allImports = new THashSet<>(Arrays.asList(imports));
    final Collection<PsiImportStatementBase> redundant;
    if (FileTypeUtils.isInServerPageFile(file)) {
      // remove only duplicate imports
      redundant = ContainerUtil.newIdentityTroveSet();
      ContainerUtil.addAll(redundant, imports);
      redundant.removeAll(allImports);
      for (PsiImportStatementBase importStatement : imports) {
        if (importStatement instanceof JspxImportStatement && importStatement.isForeignFileImport()) {
          redundant.remove(importStatement);
        }
      }
    }
    else {
      redundant = allImports;
      final List<PsiFile> roots = file.getViewProvider().getAllFiles();
      for (PsiElement root : roots) {
        root.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            if (!reference.isQualified()) {
              final JavaResolveResult resolveResult = reference.advancedResolve(false);
              if (!inTheSamePackage(file, resolveResult.getElement())) {
                final PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
                if (resolveScope instanceof PsiImportStatementBase) {
                  final PsiImportStatementBase importStatementBase = (PsiImportStatementBase)resolveScope;
                  redundant.remove(importStatementBase);
                }
              }
            }
            super.visitReferenceElement(reference);
          }

          private boolean inTheSamePackage(PsiJavaFile file, PsiElement element) {
            if (element instanceof PsiClass && ((PsiClass)element).getContainingClass() == null) {
              final PsiFile containingFile = element.getContainingFile();
              if (containingFile instanceof PsiJavaFile) {
                return Comparing.strEqual(file.getPackageName(), ((PsiJavaFile)containingFile).getPackageName());
              }
            }
            return false;
          }
        });
      }
    }
    return redundant;
  }

  @Override
  public int findEntryIndex(@NotNull PsiImportStatementBase statement) {
    return new ImportHelper(JavaCodeStyleSettings.getInstance(statement.getContainingFile())).findEntryIndex(statement);
  }

  @NotNull
  @Override
  public SuggestedNameInfo suggestCompiledParameterName(@NotNull PsiType type) {
    // avoid hang due to nice name evaluation that uses indices for resolve (IDEA-116803)
    return new SuggestedNameInfo(suggestVariableNameByType(type, VariableKind.PARAMETER, true, true)) {
    };
  }

  @NotNull
  @Override
  public SuggestedNameInfo suggestVariableName(@NotNull final VariableKind kind,
                                               @Nullable final String propertyName,
                                               @Nullable final PsiExpression expr,
                                               @Nullable PsiType type,
                                               final boolean correctKeywords) {
    LinkedHashSet<String> names = new LinkedHashSet<>();

    if (expr != null && type == null) {
      type = expr.getType();
    }

    if (propertyName != null) {
      String[] namesByName = getSuggestionsByName(propertyName, kind, correctKeywords).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
      sortVariableNameSuggestions(namesByName, kind, propertyName, null);
      ContainerUtil.addAll(names, namesByName);
    }

    final NamesByExprInfo namesByExpr;
    if (expr != null) {
      namesByExpr = suggestVariableNameByExpression(expr, kind);
      String[] suggestions = getSuggestionsByNames(namesByExpr.names, kind, correctKeywords).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
      if (namesByExpr.propertyName != null) {
        sortVariableNameSuggestions(suggestions, kind, namesByExpr.propertyName, null);
      }
      ContainerUtil.addAll(names, suggestions);
    }
    else {
      namesByExpr = null;
    }

    if (type != null) {
      String[] namesByType = suggestVariableNameByType(type, kind, correctKeywords);
      sortVariableNameSuggestions(namesByType, kind, null, type);
      ContainerUtil.addAll(names, namesByType);
    }

    final String _propertyName;
    if (propertyName != null) {
      _propertyName = propertyName;
    }
    else {
      _propertyName = namesByExpr != null ? namesByExpr.propertyName : null;
    }

    addNamesFromStatistics(names, kind, _propertyName, type);

    String[] namesArray = ArrayUtil.toStringArray(names);
    sortVariableNameSuggestions(namesArray, kind, _propertyName, type);

    final PsiType _type = type;
    return new SuggestedNameInfo(namesArray) {
      @Override
      public void nameChosen(String name) {
        if (_propertyName != null || _type != null && _type.isValid()) {
          JavaStatisticsManager.incVariableNameUseCount(name, kind, _propertyName, _type);
        }
      }
    };
  }

  private static void addNamesFromStatistics(@NotNull Set<? super String> names, @NotNull VariableKind variableKind, @Nullable String propertyName, @Nullable PsiType type) {
    String[] allNames = JavaStatisticsManager.getAllVariableNamesUsed(variableKind, propertyName, type);

    int maxFrequency = 0;
    for (String name : allNames) {
      int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
      maxFrequency = Math.max(maxFrequency, count);
    }

    int frequencyLimit = Math.max(5, maxFrequency / 2);

    for (String name : allNames) {
      if( names.contains( name ) )
      {
        continue;
      }
      int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
      if (LOG.isDebugEnabled()) {
        LOG.debug("new name:" + name + " count:" + count);
        LOG.debug("frequencyLimit:" + frequencyLimit);
      }
      if (count >= frequencyLimit) {
        names.add(name);
      }
    }

    if (propertyName != null && type != null) {
      addNamesFromStatistics(names, variableKind, propertyName, null);
      addNamesFromStatistics(names, variableKind, null, type);
    }
  }

  @NotNull
  private String[] suggestVariableNameByType(@NotNull PsiType type, @NotNull VariableKind variableKind, boolean correctKeywords) {
    return suggestVariableNameByType(type, variableKind, correctKeywords, false);
  }

  @NotNull
  private String[] suggestVariableNameByType(@NotNull PsiType type, @NotNull final VariableKind variableKind, final boolean correctKeywords, boolean skipIndices) {
    Collection<String> byTypeNames = doSuggestNamesByType(type, variableKind, skipIndices);
    return getSuggestionsByNames(byTypeNames, variableKind, correctKeywords).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @NotNull
  private Collection<String> doSuggestNamesByType(@NotNull PsiType type, @NotNull final VariableKind variableKind, boolean skipIndices) {
    final Collection<String> fromTypeMap = suggestNamesFromTypeMap(type, variableKind, skipIndices);
    if (fromTypeMap != null) {
      return fromTypeMap;
    }

    if (!(type instanceof PsiClassType) || skipIndices) {
      return suggestNamesFromTypeName(type, skipIndices);
    }

    final Collection<String> suggestions = new LinkedHashSet<>();
    final PsiClassType classType = (PsiClassType)type;
    suggestNamesForCollectionInheritors(classType, suggestions);
    suggestFromOptionalContent(variableKind, classType, suggestions);
    suggestNamesFromGenericParameters(classType, suggestions);
    suggestions.addAll(suggestNamesFromTypeName(type, false));
    suggestNamesFromHierarchy(classType, suggestions);
    return suggestions;
  }

  @Nullable
  private Collection<String> suggestNamesFromTypeMap(@NotNull PsiType type, @NotNull VariableKind variableKind, boolean skipIndices) {
    String longTypeName = skipIndices ? type.getCanonicalText() : getLongTypeName(type);
    CodeStyleSettings.TypeToNameMap map = getMapByVariableKind(variableKind);
    if (map != null && longTypeName != null) {
      if (type.equals(PsiType.NULL)) {
        longTypeName = CommonClassNames.JAVA_LANG_OBJECT;
      }
      String name = map.nameByType(longTypeName);
      if (name != null && isIdentifier(name)) {
        return Collections.singletonList(type instanceof PsiArrayType ? StringUtil.pluralize(name) : name);
      }
    }
    return null;
  }

  private void suggestFromOptionalContent(@NotNull VariableKind variableKind,
                                          @NotNull PsiClassType classType,
                                          @NotNull Collection<? super String> suggestions) {
    final PsiType optionalContent = extractOptionalContent(classType);
    if (optionalContent == null) return;

    final Collection<String> contentSuggestions = doSuggestNamesByType(optionalContent, variableKind, false);
    suggestions.addAll(contentSuggestions);
    for (String s : contentSuggestions) {
      suggestions.add("optional" + StringUtil.capitalize(s));
    }
  }

  @NotNull
  private static Collection<String> suggestNamesFromTypeName(@NotNull PsiType type, boolean skipIndices) {
    String typeName = getTypeName(type, !skipIndices);
    if (typeName == null) return Collections.emptyList();

    typeName = normalizeTypeName(typeName);
    return Collections.singletonList(type instanceof PsiArrayType ? StringUtil.pluralize(typeName) : typeName);
  }

  @Nullable
  private static PsiType extractOptionalContent(@NotNull PsiClassType classType) {
    final PsiClass resolved = classType.resolve();
    if (resolved != null && CommonClassNames.JAVA_UTIL_OPTIONAL.equals(resolved.getQualifiedName())) {
      if (classType.getParameterCount() == 1) {
        return classType.getParameters()[0];
      }
    }
    return null;
  }

  private static void suggestNamesFromHierarchy(@NotNull PsiClassType type, @NotNull Collection<? super String> suggestions) {
    final PsiClass resolved = type.resolve();
    if (resolved == null || resolved.getContainingClass() == null) return;

    InheritanceUtil.processSupers(resolved, false, superClass -> {
      if (PsiTreeUtil.isAncestor(superClass, resolved, true)) {
        suggestions.add(superClass.getName());
      }
      return false;
    });
  }

  private static void suggestNamesFromGenericParameters(@NotNull PsiClassType type, @NotNull Collection<? super String> suggestions) {
    StringBuilder fullNameBuilder = new StringBuilder();
    final PsiType[] parameters = type.getParameters();
    for (PsiType parameter : parameters) {
      if (parameter instanceof PsiClassType) {
        final String typeName = normalizeTypeName(getTypeName(parameter));
        if (typeName != null) {
          fullNameBuilder.append(typeName);
        }
      }
    }
    String baseName = normalizeTypeName(getTypeName(type));
    if (baseName != null) {
      fullNameBuilder.append(baseName);
      suggestions.add(fullNameBuilder.toString());
    }
  }

  private static void suggestNamesForCollectionInheritors(@NotNull PsiClassType type, @NotNull Collection<? super String> suggestions) {
    PsiType componentType = PsiUtil.extractIterableTypeParameter(type, false);
    if (componentType == null || componentType.equals(type)) {
      return;
    }
    String typeName = normalizeTypeName(getTypeName(componentType));
    if (typeName != null) {
      suggestions.add(StringUtil.pluralize(typeName));
    }
  }

  private static String normalizeTypeName(@Nullable String typeName) {
    if (typeName == null) {
      return null;
    }
    if (typeName.endsWith(IMPL_SUFFIX) && typeName.length() > IMPL_SUFFIX.length()) {
      return typeName.substring(0, typeName.length() - IMPL_SUFFIX.length());
    }
    return typeName;
  }

  @Nullable
  public static String getTypeName(@NotNull PsiType type) {
    return getTypeName(type, true);
  }

  @Nullable
  private static String getTypeName(@NotNull PsiType type, boolean withIndices) {
    type = type.getDeepComponentType();
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final String className = classType.getClassName();
      if (className != null || !withIndices) return className;
      final PsiClass aClass = classType.resolve();
      return aClass instanceof PsiAnonymousClass ? ((PsiAnonymousClass)aClass).getBaseClassType().getClassName() : null;
    }
    else if (type instanceof PsiPrimitiveType) {
      return type.getPresentableText();
    }
    else if (type instanceof PsiWildcardType) {
      return getTypeName(((PsiWildcardType)type).getExtendsBound(), withIndices);
    }
    else if (type instanceof PsiIntersectionType) {
      return getTypeName(((PsiIntersectionType)type).getRepresentative(), withIndices);
    }
    else if (type instanceof PsiCapturedWildcardType) {
      return getTypeName(((PsiCapturedWildcardType)type).getWildcard(), withIndices);
    }
    else if (type instanceof PsiDisjunctionType) {
      return getTypeName(((PsiDisjunctionType)type).getLeastUpperBound(), withIndices);
    }
    else {
      return null;
    }
  }

  @Nullable
  private static String getLongTypeName(@NotNull PsiType type) {
    if (type instanceof PsiClassType) {
      PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass == null) {
        return null;
      }
      else if (aClass instanceof PsiAnonymousClass) {
        PsiClass baseClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
        return baseClass != null ? baseClass.getQualifiedName() : null;
      }
      else {
        return aClass.getQualifiedName();
      }
    }
    else if (type instanceof PsiArrayType) {
      return getLongTypeName(((PsiArrayType)type).getComponentType()) + "[]";
    }
    else if (type instanceof PsiPrimitiveType) {
      return type.getPresentableText();
    }
    else if (type instanceof PsiWildcardType) {
      final PsiType bound = ((PsiWildcardType)type).getBound();
      return bound != null ? getLongTypeName(bound) : CommonClassNames.JAVA_LANG_OBJECT;
    }
    else if (type instanceof PsiCapturedWildcardType) {
      final PsiType bound = ((PsiCapturedWildcardType)type).getWildcard().getBound();
      return bound != null ? getLongTypeName(bound) : CommonClassNames.JAVA_LANG_OBJECT;
    }
    else if (type instanceof PsiIntersectionType) {
      return getLongTypeName(((PsiIntersectionType)type).getRepresentative());
    }
    else if (type instanceof PsiDisjunctionType) {
      return getLongTypeName(((PsiDisjunctionType)type).getLeastUpperBound());
    }
    else {
      return null;
    }
  }

  private static class NamesByExprInfo {
    static final NamesByExprInfo EMPTY = new NamesByExprInfo(null, Collections.emptyList());

    private final String propertyName;
    private final Collection<String> names;

    private NamesByExprInfo(@Nullable String propertyName, @NotNull Collection<String> names) {
      this.propertyName = propertyName;
      this.names = names;
    }

    private NamesByExprInfo(@NotNull String propertyName) {
      this(propertyName, Collections.singletonList(propertyName));
    }

    private NamesByExprInfo(@Nullable String propertyName, @NotNull String... names) {
      this(
        propertyName,
        propertyName == null ? Arrays.asList(names) : ContainerUtil.prepend(Arrays.asList(names), propertyName)
      );
    }
  }

  @NotNull
  private NamesByExprInfo suggestVariableNameByExpression(@NotNull PsiExpression expr, @Nullable VariableKind variableKind) {
    final LinkedHashSet<String> names = new LinkedHashSet<>();
    ContainerUtil.addIfNotNull(names, suggestVariableNameFromLiterals(expr));

    NamesByExprInfo byExpr = suggestVariableNameByExpressionOnly(expr, variableKind, false);
    NamesByExprInfo byExprPlace = suggestVariableNameByExpressionPlace(expr, variableKind);
    NamesByExprInfo byExprAllMethods = suggestVariableNameByExpressionOnly(expr, variableKind, true);

    ContainerUtil.addAll(names, byExpr.names);
    ContainerUtil.addAll(names, byExprPlace.names);

    PsiType type = expr.getType();
    if (type != null && variableKind != null) {
      ContainerUtil.addAll(names, doSuggestNamesByType(type, variableKind, false));
    }
    ContainerUtil.addAll(names, byExprAllMethods.names);

    String propertyName = byExpr.propertyName != null ? byExpr.propertyName : byExprPlace.propertyName;
    return new NamesByExprInfo(propertyName, names);
  }

  @Nullable
  private static String suggestVariableNameFromLiterals(@NotNull PsiExpression expr) {
    String text = findLiteralText(expr);
    if (text == null) return null;
    return expr.getType() instanceof PsiArrayType ? StringUtil.pluralize(text) : text;
  }

  @Nullable
  private static String findLiteralText(@NotNull PsiExpression expr) {
    final PsiElement[] literals = PsiTreeUtil.collectElements(expr, new PsiElementFilter() {
      @Override
      public boolean isAccepted(PsiElement element) {
        if (isStringPsiLiteral(element) && isNameSupplier(element)) {
          final PsiElement exprList = element.getParent();
          if (exprList instanceof PsiExpressionList) {
            final PsiElement call = exprList.getParent();
            if (call instanceof PsiNewExpression) {
              return true;
            } else if (call instanceof PsiMethodCallExpression) {
              //TODO: exclude or not getA().getB("name").getC(); or getA(getB("name").getC()); It works fine for now in the most cases
              return true;
            }
          }
        }
        return false;
      }

      private boolean isNameSupplier(PsiElement element) {
        String stringPresentation = StringUtil.unquoteString(element.getText());
        String[] words = stringPresentation.split(" ");
        if (words.length > 5) return false;
        return Arrays.stream(words).allMatch(StringUtil::isJavaIdentifier);
      }
    });

    if (literals.length == 1) {
      return StringUtil.unquoteString(literals[0].getText()).replaceAll(" ", "_");
    }
    return null;
  }

  @NotNull
  private NamesByExprInfo suggestVariableNameByExpressionOnly(@NotNull PsiExpression expr, @Nullable VariableKind variableKind, boolean useAllMethodNames) {
    if (expr instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
      String methodName = methodExpr.getReferenceName();
      if (methodName != null) {
        if ("of".equals(methodName) || "ofNullable".equals(methodName)) {
          if (isJavaUtilMethodCall((PsiMethodCallExpression)expr)) {
            PsiExpression[] expressions = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
            if (expressions.length > 0) {
              return suggestVariableNameByExpressionOnly(expressions[0], variableKind, useAllMethodNames);
            }
          }
        }
        if ("map".equals(methodName) || "flatMap".equals(methodName) || "filter".equals(methodName)) {
          if (isJavaUtilMethodCall((PsiMethodCallExpression)expr)) {
            return NamesByExprInfo.EMPTY;
          }
        }

        String[] words = NameUtil.nameToWords(methodName);
        if (words.length > 0) {
          final String firstWord = words[0];
          if (GET_PREFIX.equals(firstWord)
              || IS_PREFIX.equals(firstWord)
              || FIND_PREFIX.equals(firstWord)
              || CREATE_PREFIX.equals(firstWord)) {
            if (words.length > 1) {
              final String propertyName = methodName.substring(firstWord.length());
              final PsiExpression qualifierExpression = methodExpr.getQualifierExpression();
              if (qualifierExpression instanceof PsiReferenceExpression &&
                  ((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiVariable) {
                String name = qualifierExpression.getText() + StringUtil.capitalize(propertyName);
                return new NamesByExprInfo(propertyName, name);
              }
              return new NamesByExprInfo(propertyName);
            }
          }
          else if (words.length == 1 || useAllMethodNames) {
            return new NamesByExprInfo(methodName);
          }
        }
      }
    }
    else if (expr instanceof PsiReferenceExpression) {
      String propertyName = getPropertyName((PsiReferenceExpression)expr, true);
      if (propertyName != null) {
        return new NamesByExprInfo(propertyName);
      }
    }
    else if (expr instanceof PsiArrayAccessExpression) {
      NamesByExprInfo info = suggestVariableNameByExpressionOnly(((PsiArrayAccessExpression)expr).getArrayExpression(), variableKind, useAllMethodNames);

      String singular = info.propertyName == null ? null : StringUtil.unpluralize(info.propertyName);
      if (singular != null) {
        return new NamesByExprInfo(singular, ContainerUtil.mapNotNull(info.names, StringUtil::unpluralize));
      }
    }
    else if (expr instanceof PsiLiteralExpression && variableKind == VariableKind.STATIC_FINAL_FIELD) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expr;
      final Object value = literalExpression.getValue();
      if (value instanceof String) {
        final String stringValue = (String)value;
        String[] names = getSuggestionsByValue(stringValue);
        if (names.length > 0) {
          return new NamesByExprInfo(null, constantValueToConstantName(names));
        }
      }
    } else if (expr instanceof PsiParenthesizedExpression) {
      final PsiExpression expression = ((PsiParenthesizedExpression)expr).getExpression();
      if (expression != null) {
        return suggestVariableNameByExpressionOnly(expression, variableKind, useAllMethodNames);
      }
    } else if (expr instanceof PsiTypeCastExpression) {
      final PsiExpression operand = ((PsiTypeCastExpression)expr).getOperand();
      if (operand != null) {
        return suggestVariableNameByExpressionOnly(operand, variableKind, useAllMethodNames);
      }
    } else if (expr instanceof PsiLiteralExpression) {
      final String text = StringUtil.unquoteString(expr.getText());
      if (isIdentifier(text)) {
        return new NamesByExprInfo(text);
      }
    } else if (expr instanceof PsiFunctionalExpression && variableKind != null) {
      final PsiType functionalInterfaceType = ((PsiFunctionalExpression)expr).getFunctionalInterfaceType();
      if (functionalInterfaceType != null) {
        return new NamesByExprInfo(null, doSuggestNamesByType(functionalInterfaceType, variableKind, false));
      }
    }

    return NamesByExprInfo.EMPTY;
  }

  private static boolean isJavaUtilMethodCall(@NotNull PsiMethodCallExpression expr) {
    PsiMethod method = expr.resolveMethod();
    if (method == null) return false;

    return isJavaUtilMethod(method) || !MethodDeepestSuperSearcher.processDeepestSuperMethods(method, method1 -> !isJavaUtilMethod(method1));
  }

  private static boolean isJavaUtilMethod(@NotNull PsiMethod method) {
    String name = PsiUtil.getMemberQualifiedName(method);
    return name != null && name.startsWith("java.util.");
  }

  @Nullable
  private String getPropertyName(@NotNull PsiReferenceExpression expression) {
    return getPropertyName(expression, false);
  }

  @Nullable
  private String getPropertyName(@NotNull PsiReferenceExpression expression, boolean skipUnresolved) {
    String propertyName = expression.getReferenceName();
    if (propertyName == null) return null;

    PsiElement refElement = expression.resolve();
    if (refElement instanceof PsiVariable) {
      VariableKind refVariableKind = getVariableKind((PsiVariable)refElement);
      return variableNameToPropertyName(propertyName, refVariableKind);
    }
    else if (refElement == null && skipUnresolved) {
      return null;
    }
    else {
      return propertyName;
    }
  }

  @NotNull
  private static String constantValueToConstantName(@NotNull String[] names) {
    return String.join("_", names);
  }

  @NotNull
  private static String[] getSuggestionsByValue(@NotNull String stringValue) {
    List<String> result = new ArrayList<>();
    StringBuffer currentWord = new StringBuffer();

    boolean prevIsUpperCase  = false;

    for (int i = 0; i < stringValue.length(); i++) {
      final char c = stringValue.charAt(i);
      if (Character.isUpperCase(c)) {
        if (currentWord.length() > 0 && !prevIsUpperCase) {
          result.add(currentWord.toString());
          currentWord = new StringBuffer();
        }
        currentWord.append(c);
      } else if (Character.isLowerCase(c)) {
        currentWord.append(Character.toUpperCase(c));
      } else if (Character.isJavaIdentifierPart(c) && c != '_') {
        if (Character.isJavaIdentifierStart(c) || currentWord.length() > 0 || !result.isEmpty()) {
          currentWord.append(c);
        }
      } else {
        if (currentWord.length() > 0) {
          result.add(currentWord.toString());
          currentWord = new StringBuffer();
        }
      }

      prevIsUpperCase = Character.isUpperCase(c);
    }

    if (currentWord.length() > 0) {
      result.add(currentWord.toString());
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  private NamesByExprInfo suggestVariableNameByExpressionPlace(@NotNull PsiExpression expr, @Nullable VariableKind variableKind) {
    if (expr.getParent() instanceof PsiExpressionList) {
      PsiExpressionList list = (PsiExpressionList)expr.getParent();
      PsiElement listParent = list.getParent();
      PsiSubstitutor subst = PsiSubstitutor.EMPTY;
      PsiMethod method = null;
      if (listParent instanceof PsiMethodCallExpression) {
        final JavaResolveResult resolveResult = ((PsiMethodCallExpression)listParent).getMethodExpression().advancedResolve(false);
        method = (PsiMethod)resolveResult.getElement();
        subst = resolveResult.getSubstitutor();
      }
      else {
        if (listParent instanceof PsiAnonymousClass) {
          listParent = listParent.getParent();
        }
        if (listParent instanceof PsiNewExpression) {
          method = ((PsiNewExpression)listParent).resolveConstructor();
        }
      }

      if (method != null) {
        final PsiElement navElement = method.getNavigationElement();
        if (navElement instanceof PsiMethod) {
          method = (PsiMethod)navElement;
        }
        PsiExpression[] expressions = list.getExpressions();
        int index = ArrayUtil.indexOf(expressions, expr);
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (index < parameters.length) {
          String name = parameters[index].getName();
          if (name != null && TypeConversionUtil.areTypesAssignmentCompatible(subst.substitute(parameters[index].getType()), expr)) {
            name = variableNameToPropertyName(name, VariableKind.PARAMETER);
            if (expressions.length == 1) {
              final String methodName = method.getName();
              String[] words = NameUtil.nameToWords(methodName);
              if (words.length > 0) {
                final String firstWord = words[0];
                if (SET_PREFIX.equals(firstWord)) {
                  final String propertyName = methodName.substring(firstWord.length());
                  return new NamesByExprInfo(name, propertyName);
                }
              }
            }
            return new NamesByExprInfo(name);
          }
        }
      }
    }
    else if (expr.getParent() instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expr.getParent();
      if (expr == assignmentExpression.getRExpression()) {
        final PsiExpression leftExpression = assignmentExpression.getLExpression();
        if (leftExpression instanceof PsiReferenceExpression) {
          String name = getPropertyName((PsiReferenceExpression)leftExpression);
          if (name != null) {
            return new NamesByExprInfo(name);
          }
        }
      }
    }
     //skip places where name for this local variable is calculated, otherwise grab the name 
    else if (expr.getParent() instanceof PsiLocalVariable && variableKind != VariableKind.LOCAL_VARIABLE) {
      PsiVariable variable = (PsiVariable)expr.getParent();
      String variableName = variable.getName();
      if (variableName != null) {
        String propertyName = variableNameToPropertyName(variableName, getVariableKind(variable));
        return new NamesByExprInfo(propertyName);
      }
    }

    return NamesByExprInfo.EMPTY;
  }

  @NotNull
  @Override
  public String variableNameToPropertyName(@NotNull String name, @NotNull VariableKind variableKind) {
    if (variableKind == VariableKind.STATIC_FINAL_FIELD || variableKind == VariableKind.STATIC_FIELD && name.contains("_")) {
      StringBuilder buffer = new StringBuilder();
      for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (c != '_') {
          if( Character.isLowerCase( c ) )
          {
            return variableNameToPropertyNameInner( name, variableKind );
          }

          buffer.append(Character.toLowerCase(c));
        continue;
        }
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i < name.length()) {
          c = name.charAt(i);
          buffer.append(c);
        }
      }
      return buffer.toString();
    }

    return variableNameToPropertyNameInner(name, variableKind);
  }

  @NotNull
  private String variableNameToPropertyNameInner(@NotNull String name, @NotNull VariableKind variableKind) {
    String prefix = getPrefixByVariableKind(variableKind);
    String suffix = getSuffixByVariableKind(variableKind);
    boolean doDecapitalize = false;

    int pLength = prefix.length();
    if (pLength > 0 && name.startsWith(prefix) && name.length() > pLength &&
        // check it's not just a long camel word that happens to begin with the specified prefix
        (!Character.isLetter(prefix.charAt(pLength - 1)) || Character.isUpperCase(name.charAt(pLength)))) {
      name = name.substring(pLength);
      doDecapitalize = true;
    }

    if (name.endsWith(suffix) && name.length() > suffix.length()) {
      name = name.substring(0, name.length() - suffix.length());
      doDecapitalize = true;
    }

    if (doDecapitalize) {
      name = Introspector.decapitalize(name);
    }

    return name;
  }

  @NotNull
  @Override
  public String propertyNameToVariableName(@NotNull String propertyName, @NotNull VariableKind variableKind) {
    if (variableKind == VariableKind.STATIC_FINAL_FIELD) {
      String[] words = NameUtil.nameToWords(propertyName);
      return StringUtil.join(words, StringUtil::toUpperCase, "_");
    }

    String prefix = getPrefixByVariableKind(variableKind);
    String name = propertyName;
    if (!name.isEmpty() && !prefix.isEmpty() && !StringUtil.endsWithChar(prefix, '_')) {
      name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    name = prefix + name + getSuffixByVariableKind(variableKind);
    name = changeIfNotIdentifier(name);
    return name;
  }

  @NotNull
  private Collection<String> getSuggestionsByNames(@NotNull Iterable<String> names, @NotNull VariableKind kind, boolean correctKeywords) {
    final Collection<String> suggestions = new LinkedHashSet<>();
    for (String name : names) {
      suggestions.addAll(getSuggestionsByName(name, kind, correctKeywords));
    }
    return suggestions;
  }

  @NotNull
  private Collection<String> getSuggestionsByName(@NotNull String name, @NotNull VariableKind variableKind, boolean correctKeywords) {
    boolean upperCaseStyle = variableKind == VariableKind.STATIC_FINAL_FIELD;
    boolean preferLongerNames = getJavaSettings().PREFER_LONGER_NAMES;
    String prefix = getPrefixByVariableKind(variableKind);
    String suffix = getSuffixByVariableKind(variableKind);

    List<String> answer = new ArrayList<>();
    for (String suggestion : NameUtil.getSuggestionsByName(name, prefix, suffix, upperCaseStyle, preferLongerNames, false)) {
      answer.add(correctKeywords ? changeIfNotIdentifier(suggestion) : suggestion);
    }

    ContainerUtil.addIfNotNull(answer, getWordByPreposition(name, prefix, suffix, upperCaseStyle));
    return answer;
  }

  private static String getWordByPreposition(@NotNull String name, String prefix, String suffix, boolean upperCaseStyle) {
    String[] words = NameUtil.splitNameIntoWords(name);
    for (int i = 1; i < words.length; i++) {
      for (String preposition : ourPrepositions) {
        if (preposition.equalsIgnoreCase(words[i])) {
          String mainWord = words[i - 1];
          if (upperCaseStyle) {
            mainWord = StringUtil.toUpperCase(mainWord);
          }
          else {
            if (prefix.isEmpty() || StringUtil.endsWithChar(prefix, '_')) {
              mainWord = StringUtil.toLowerCase(mainWord);
            }
            else {
              mainWord = StringUtil.capitalize(mainWord);
            }
          }
          return prefix + mainWord + suffix;
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String suggestUniqueVariableName(@NotNull String baseName, PsiElement place, boolean lookForward) {
    return suggestUniqueVariableName(baseName, place, lookForward, false, v -> false);
  }

  @NotNull
  @Override
  public String suggestUniqueVariableName(@NotNull String baseName, PsiElement place, Predicate<PsiVariable> canBeReused) {
    return suggestUniqueVariableName(baseName, place, true, false, canBeReused);
  }

  @Override
  @NotNull
  public SuggestedNameInfo suggestUniqueVariableName(@NotNull final SuggestedNameInfo baseNameInfo,
                                                     PsiElement place,
                                                     boolean ignorePlaceName,
                                                     boolean lookForward) {
    final String[] names = baseNameInfo.names;
    final LinkedHashSet<String> uniqueNames = new LinkedHashSet<>(names.length);
    for (String name : names) {
      if (ignorePlaceName && place instanceof PsiNamedElement) {
        final String placeName = ((PsiNamedElement)place).getName();
        if (Comparing.strEqual(placeName, name)) {
          uniqueNames.add(name);
          continue;
        }
      }
      String unique = suggestUniqueVariableName(name, place, lookForward);
      if (!unique.equals(name)) {
        String withShadowing = suggestUniqueVariableName(name, place, lookForward, true, v -> false);
        if (withShadowing.equals(name)) {
          uniqueNames.add(name);
        }
      }
      uniqueNames.add(unique);
    }

    return new SuggestedNameInfo(ArrayUtil.toStringArray(uniqueNames)) {
      @Override
      public void nameChosen(String name) {
        baseNameInfo.nameChosen(name);
      }
    };
  }

  @NotNull
  private static String suggestUniqueVariableName(@NotNull String baseName,
                                                  PsiElement place,
                                                  boolean lookForward,
                                                  boolean allowShadowing,
                                                  Predicate<? super PsiVariable> canBeReused) {
    PsiElement scope = PsiTreeUtil.getNonStrictParentOfType(place, PsiStatement.class, PsiCodeBlock.class, PsiMethod.class);
    for (int index = 0; ; index++) {
      String name = index > 0 ? baseName + index : baseName;
      if (hasConflictingVariable(place, name, allowShadowing) ||
          lookForward && hasConflictingVariableAfterwards(scope, name, canBeReused)) {
        continue;
      }
      return name;
    }
  }

  private static boolean hasConflictingVariable(@Nullable PsiElement place, @NotNull String name, boolean allowShadowing) {
    if (place == null) {
      return false;
    }
    PsiResolveHelper helper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
    PsiVariable existingVariable = helper.resolveAccessibleReferencedVariable(name, place);
    if (existingVariable == null) return false;

    if (allowShadowing && existingVariable instanceof PsiField && PsiTreeUtil.getNonStrictParentOfType(place, PsiMethod.class) != null) {
      return false;
    }
    
    return true;
  }

  public static boolean hasConflictingVariableAfterwards(@Nullable PsiElement scope,
                                                         @NotNull final String name,
                                                         @NotNull Predicate<? super PsiVariable> canBeReused) {
    PsiElement run = scope;
    while (run != null) {
      class CancelException extends RuntimeException {
      }
      try {
        run.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitClass(final PsiClass aClass) {}

          @Override public void visitVariable(PsiVariable variable) {
            if (name.equals(variable.getName()) && !canBeReused.test(variable)) {
              throw new CancelException();
            }
          }
        });
      }
      catch (CancelException e) {
        return true;
      }
      run = run.getNextSibling();
      if (scope instanceof PsiMethod || scope instanceof PsiForeachStatement) {//do not check next member for param name conflict
        break;
      }
    }
    return false;
  }

  private static void sortVariableNameSuggestions(@NotNull String[] names,
                                                  @NotNull final VariableKind variableKind,
                                                  @Nullable final String propertyName,
                                                  @Nullable final PsiType type) {
    if( names.length <= 1 ) {
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("sorting names:" + variableKind);
      if (propertyName != null) {
        LOG.debug("propertyName:" + propertyName);
      }
      if (type != null) {
        LOG.debug("type:" + type);
      }
      for (String name : names) {
        int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
        LOG.debug(name + " : " + count);
      }
    }

    Comparator<String> comparator = (s1, s2) -> {
      int count1 = JavaStatisticsManager.getVariableNameUseCount(s1, variableKind, propertyName, type);
      int count2 = JavaStatisticsManager.getVariableNameUseCount(s2, variableKind, propertyName, type);
      return count2 - count1;
    };
    Arrays.sort(names, comparator);
  }

  @Override
  @NotNull
  public String getPrefixByVariableKind(@NotNull VariableKind variableKind) {
    String prefix = null;
    switch (variableKind) {
      case FIELD:
        prefix = getJavaSettings().FIELD_NAME_PREFIX;
        break;
      case STATIC_FIELD:
        prefix = getJavaSettings().STATIC_FIELD_NAME_PREFIX;
        break;
      case PARAMETER:
        prefix = getJavaSettings().PARAMETER_NAME_PREFIX;
        break;
      case LOCAL_VARIABLE:
        prefix = getJavaSettings().LOCAL_VARIABLE_NAME_PREFIX;
        break;
      case STATIC_FINAL_FIELD:
        break;
      default:
        LOG.assertTrue(false);
        break;
    }
    return prefix == null ? "" : prefix;
  }

  @Override
  @NotNull
  public String getSuffixByVariableKind(@NotNull VariableKind variableKind) {
    String suffix = null;
    switch (variableKind) {
      case FIELD:
        suffix = getJavaSettings().FIELD_NAME_SUFFIX;
        break;
      case STATIC_FIELD:
        suffix = getJavaSettings().STATIC_FIELD_NAME_SUFFIX;
        break;
      case PARAMETER:
        suffix = getJavaSettings().PARAMETER_NAME_SUFFIX;
        break;
      case LOCAL_VARIABLE:
        suffix = getJavaSettings().LOCAL_VARIABLE_NAME_SUFFIX;
        break;
      case STATIC_FINAL_FIELD:
        break;
      default:
        LOG.assertTrue(false);
        break;
    }
    return suffix == null ? "" : suffix;
  }

  @NotNull
  @Override
  public Collection<String> suggestSemanticNames(@NotNull PsiExpression expression) {
    return suggestVariableNameByExpression(expression, null).names;
  }

  @NotNull
  private Collection<String> suggestSemanticNamesByType(@Nullable PsiType type, @NotNull VariableKind kind) {
    return type == null ? Collections.emptyList() : doSuggestNamesByType(type, kind, false);
  }

  @NotNull
  public SuggestedNameInfo suggestNames(@NotNull Collection<String> semanticNames, @NotNull VariableKind kind, @Nullable PsiType type) {
    final Iterable<String> allSemanticNames = ContainerUtil.concat(
      semanticNames,
      suggestSemanticNamesByType(type, kind)
    );

    final Set<String> suggestions = new LinkedHashSet<>(getSuggestionsByNames(allSemanticNames, kind, true));
    final String propertyName = ContainerUtil.getFirstItem(semanticNames);
    addNamesFromStatistics(suggestions, kind, propertyName, type);

    String[] namesArray = suggestions.toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    sortVariableNameSuggestions(namesArray, kind, propertyName, type);
    if (type == null) {
      return new SuggestedNameInfo(namesArray) {
        @Override
        public void nameChosen(String name) {}
      };
    }
    else {
      return new SuggestedNameInfo(namesArray) {
        @Override
        public void nameChosen(String name) {
          if (!type.isValid()) return;
          JavaStatisticsManager.incVariableNameUseCount(name, kind, propertyName, type);
        }
      };
    }
  }

  @Nullable
  private CodeStyleSettings.TypeToNameMap getMapByVariableKind(@NotNull VariableKind variableKind) {
    if (variableKind == VariableKind.FIELD) return getJavaSettings().FIELD_TYPE_TO_NAME;
    if (variableKind == VariableKind.STATIC_FIELD) return getJavaSettings().STATIC_FIELD_TYPE_TO_NAME;
    if (variableKind == VariableKind.PARAMETER) return getJavaSettings().PARAMETER_TYPE_TO_NAME;
    if (variableKind == VariableKind.LOCAL_VARIABLE) return getJavaSettings().LOCAL_VARIABLE_TYPE_TO_NAME;
    return null;
  }

  @NonNls
  @NotNull
  private String changeIfNotIdentifier(@NotNull String name) {
    if (!isIdentifier(name)) {
      return StringUtil.fixVariableNameDerivedFromPropertyName(name);
    }
    return name;
  }

  private boolean isIdentifier(@NotNull String name) {
    return PsiNameHelper.getInstance(myProject).isIdentifier(name, LanguageLevel.HIGHEST);
  }

  @NotNull
  private JavaCodeStyleSettings getJavaSettings() {
    return CodeStyle.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class);
  }

  private static boolean isStringPsiLiteral(@NotNull PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      final String text = element.getText();
      return StringUtil.isQuotedString(text);
    }
    return false;
  }
}
