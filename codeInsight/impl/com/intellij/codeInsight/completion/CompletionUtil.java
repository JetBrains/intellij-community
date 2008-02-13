package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageWordCompletion;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.position.SuperParentFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Compiler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CompletionUtil {
  public static final Key<TailType> TAIL_TYPE_ATTR = LookupItem.TAIL_TYPE_ATTR;

  private static final Key<SmartPsiElementPointer> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  private static final NotNullLazyValue<CompletionData> ourJavaCompletionData = new NotNullLazyValue<CompletionData>() {
    @NotNull
    protected CompletionData compute() {
      return new JavaCompletionData();
    }
  };
  private static final NotNullLazyValue<CompletionData> ourJava15CompletionData = new NotNullLazyValue<CompletionData>() {
    @NotNull
    protected CompletionData compute() {
      return new Java15CompletionData();
    }
  };
  private static final CompletionData ourGenericCompletionData = new CompletionData() {
    {
      final CompletionVariant variant = new CompletionVariant(PsiElement.class, TrueFilter.INSTANCE);
      variant.addCompletionFilter(TrueFilter.INSTANCE, TailType.NONE);
      registerVariant(variant);
    }
  };
  private static final CompletionData ourWordCompletionData = new WordCompletionData();
  private static final NotNullLazyValue<CompletionData> ourJavaDocCompletionData = new NotNullLazyValue<CompletionData>() {
    @NotNull
    protected CompletionData compute() {
      return new JavaDocCompletionData();
    }
  };

  private static HashMap<FileType, NotNullLazyValue<CompletionData>> ourCustomCompletionDatas = new HashMap<FileType, NotNullLazyValue<CompletionData>>();
  private static final int MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED = 50000;
  @NonNls
  public static final String GET_PREFIX = "get";
  @NonNls
  public static final String SET_PREFIX = "set";
  @NonNls
  public static final String IS_PREFIX = "is";

  public static final @NonNls String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz ";
  public static final @NonNls String DUMMY_IDENTIFIER_TRIMMED = DUMMY_IDENTIFIER.trim();
  public static final Key<String> COMPLETION_PREFIX = Key.create("Completion prefix");
  public static final Key<PsiElement> ORIGINAL_KEY = Key.create("ORIGINAL_KEY");

  public static PsiType getQualifierType(LookupItem item) {
    return (PsiType)item.getAttribute(QUALIFIER_TYPE_ATTR);
  }

  @NotNull
  public static <T extends PsiElement> T getOriginalElement(@NotNull T element) {
    final T data = (T)element.getUserData(ORIGINAL_KEY);
    return data != null ? data : element;
  }

  public static void setQualifierType(LookupItem item, PsiType type) {
    if (type != null) {
      item.setAttribute(QUALIFIER_TYPE_ATTR, type);
    }
    else {
      item.setAttribute(QUALIFIER_TYPE_ATTR, null);
    }
  }

  public static boolean startsWith(String text, String prefix) {
    //if (text.length() <= prefix.length()) return false;
    return toLowerCase(text).startsWith(toLowerCase(prefix));
  }

  private static String toLowerCase(String text) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    switch (settings.COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return text.toLowerCase();

      case CodeInsightSettings.FIRST_LETTER: {
        StringBuffer buffer = new StringBuffer();
        buffer.append(text.toLowerCase());
        if (buffer.length() > 0) {
          buffer.setCharAt(0, text.charAt(0));
        }
        return buffer.toString();
      }

      default:
        return text;
    }
  }

  static void highlightMembersOfContainer(Set<LookupItem> set) {
    for (final LookupItem item : set) {
      highlightMemberOfContainer(item);
    }
  }

  public static void highlightMemberOfContainer(final LookupItem item) {
    Object o = item.getObject();
    PsiType qualifierType = getQualifierType(item);
    if (qualifierType == null) return;
    if (qualifierType instanceof PsiArrayType) {
      if (o instanceof PsiField || o instanceof PsiMethod || o instanceof PsiClass) {
        PsiElement parent = ((PsiElement)o).getParent();
        if (parent instanceof PsiClass && parent.getContainingFile().getVirtualFile() == null) { //?
          item.setAttribute(LookupItem.HIGHLIGHTED_ATTR, "");
        }
      }
    }
    else if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (o instanceof PsiField || o instanceof PsiMethod || o instanceof PsiClass) {
        PsiElement parent = ((PsiElement)o).getParent();
        if (parent != null && parent.equals(qualifierClass)) {
          item.setAttribute(LookupItem.HIGHLIGHTED_ATTR, "");
        }
      }
    }
  }

  private static boolean hasNonSoftReference(final PsiFile file, final int startOffset) {
    return isNonSoftReference(file.findReferenceAt(startOffset));
  }

  private static boolean isNonSoftReference(final PsiReference reference) {
    if (reference instanceof PsiMultiReference) {
      for (final PsiReference psiReference : ((PsiMultiReference)reference).getReferences()) {
        if (isNonSoftReference(psiReference)) return true;
      }
    }
    return reference != null && !reference.isSoft();
  }

  public static CompletionData getCompletionDataByElement(PsiElement element, final PsiFile file, final int startOffset) {
    CompletionData wordCompletionData = null;
    if (!hasNonSoftReference(file, startOffset)) {
      ASTNode textContainer = element != null ? element.getNode() : null;
      while (textContainer != null) {
        final IElementType elementType = textContainer.getElementType();
        if (LanguageWordCompletion.INSTANCE.isEnabledIn(elementType) || elementType == PlainTextTokenTypes.PLAIN_TEXT) {
          wordCompletionData = ourWordCompletionData;
        }
        textContainer = textContainer.getTreeParent();
      }
    }
    final CompletionData completionDataByElementInner = getCompletionDataByElementInner(element, file);
    if (wordCompletionData != null) return new CompositeCompletionData(completionDataByElementInner, wordCompletionData);
    return completionDataByElementInner;
  }

  public static CompletionData getCompletionDataByElementInner(PsiElement element, final PsiFile file) {
    final CompletionData completionDataByFileType = getCompletionDataByFileType(file.getFileType());
    if (completionDataByFileType != null) return completionDataByFileType;

    if ((file.getViewProvider().getPsi(StdLanguages.JAVA) != null)) {
      if (element != null && new SuperParentFilter(new ClassFilter(PsiDocComment.class)).isAcceptable(element, element.getParent())) {
        return ourJavaDocCompletionData.getValue();
      }
      return element != null && PsiUtil.getLanguageLevel(element).equals(LanguageLevel.JDK_1_5)
             ? ourJava15CompletionData.getValue()
             : ourJavaCompletionData.getValue();
    }
    return ourGenericCompletionData;
  }

  public static void registerCompletionData(FileType fileType, NotNullLazyValue<CompletionData> completionData) {
    ourCustomCompletionDatas.put(fileType, completionData);
  }
  
  public static void registerCompletionData(FileType fileType, final CompletionData completionData) {
    registerCompletionData(fileType, new NotNullLazyValue<CompletionData>() {
      @NotNull
      protected CompletionData compute() {
        return completionData;
      }
    });
  }

  public static CompletionData getCompletionDataByFileType(FileType fileType) {
    for(CompletionDataEP ep: Extensions.getExtensions(CompletionDataEP.EP_NAME)) {
      if (ep.fileType.equals(fileType.getName())) {
        return ep.getHandler();
      }
    }
    final NotNullLazyValue<CompletionData> lazyValue = ourCustomCompletionDatas.get(fileType);
    return lazyValue == null ? null : lazyValue.getValue();
  }


  public static Pattern createCamelHumpsMatcher(String pattern) {
    Pattern pat = null;
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    int variant = settings.COMPLETION_CASE_SENSITIVE;
    Perl5Compiler compiler = new Perl5Compiler();

    try {
      switch (variant) {
        case CodeInsightSettings.NONE:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 0, true, true));
          break;
        case CodeInsightSettings.FIRST_LETTER:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 1, true, true));
          break;
        case CodeInsightSettings.ALL:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 0, false, false));
          break;
        default:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 1, true, false));
      }
    }
    catch (MalformedPatternException me) {
    }
    return pat;
  }


  public static LookupItemPreferencePolicy completeVariableNameForRefactoring(Project project, Set<LookupItem> set, String prefix, PsiType varType, VariableKind varKind) {
    return completeVariableNameForRefactoring(project, set, new CamelHumpMatcher(prefix), varType, varKind);
  }
  public static LookupItemPreferencePolicy completeVariableNameForRefactoring(Project project, Set<LookupItem> set, PrefixMatcher matcher, PsiType varType, VariableKind varKind) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    final String[] suggestedNames = suggestedNameInfo.names;
    tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNames, matcher), suggestedNameInfo);

    if (set.isEmpty() && PsiType.VOID != varType) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
      final String prefix = matcher.getPrefix();
      final boolean isMethodPrefix = prefix.startsWith(IS_PREFIX) || prefix.startsWith(GET_PREFIX) || prefix.startsWith(SET_PREFIX);
      if (varKind != VariableKind.STATIC_FINAL_FIELD || isMethodPrefix) {
        for (int i = 0; i < suggestedNames.length; i++) {
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], varKind);
        }
      }

      suggestedNameInfo = new SuggestedNameInfo(getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix)) {
        public void nameChoosen(String name) {
        }
      };

      tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, matcher), suggestedNameInfo);
    }
    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public static void tunePreferencePolicy(final List<LookupItem> list, final SuggestedNameInfo suggestedNameInfo) {
    final SimpleInsertHandler insertHandler = new SimpleInsertHandler() {
        public int handleInsert(final Editor editor,
                                final int startOffset,
                                final LookupElement item,
                                final LookupElement[] allItems,
                                final TailType tailType) throws IncorrectOperationException {
          suggestedNameInfo.nameChoosen(item.getLookupString());
          return editor.getCaretModel().getOffset();
        }

      public boolean equals(final Object obj) {
        return obj.getClass() == getClass();
      }

      public int hashCode() {
        return 42;
      }
    };

    for (int i = 0; i < list.size(); i++) {
      LookupItem item = list.get(i);
      item.setPriority(list.size() - i).setInsertHandler(insertHandler);
    }
  }

  public static String[] getOverlappedNameVersions(final String prefix, final String[] suggestedNames, String suffix) {
    final List<String> newSuggestions = new ArrayList<String>();
    int longestOverlap = 0;

    for (String suggestedName : suggestedNames) {
      if (suggestedName.toUpperCase().startsWith(prefix.toUpperCase())) {
        newSuggestions.add(suggestedName);
        longestOverlap = prefix.length();
      }

      suggestedName = String.valueOf(Character.toUpperCase(suggestedName.charAt(0))) + suggestedName.substring(1);
      final int overlap = getOverlap(suggestedName, prefix);

      if (overlap < longestOverlap) continue;

      if (overlap > longestOverlap) {
        newSuggestions.clear();
        longestOverlap = overlap;
      }

      String suggestion = prefix.substring(0, prefix.length() - overlap) + suggestedName;

      final int lastIndexOfSuffix = suggestion.lastIndexOf(suffix);
      if (lastIndexOfSuffix >= 0 && suffix.length() < suggestion.length() - lastIndexOfSuffix) {
        suggestion = suggestion.substring(0, lastIndexOfSuffix) + suffix;
      }

      if (!newSuggestions.contains(suggestion)) {
        newSuggestions.add(suggestion);
      }
    }
    return newSuggestions.toArray(new String[newSuggestions.size()]);
  }

  private static int getOverlap(final String propertyName, final String prefix) {
    int overlap = 0;
    int propertyNameLen = propertyName.length();
    int prefixLen = prefix.length();
    for (int j = 1; j < prefixLen && j < propertyNameLen; j++) {
      if (prefix.substring(prefixLen - j).equals(propertyName.substring(0, j))) {
        overlap = j;
      }
    }
    return overlap;
  }

  public static PsiType eliminateWildcards(PsiType type) {
    return eliminateWildcardsInner(type, true);
  }

  private static PsiType eliminateWildcardsInner(PsiType type, final boolean eliminateInTypeArguments) {
    if (eliminateInTypeArguments && type instanceof PsiClassType) {
      PsiClassType classType = ((PsiClassType)type);
      JavaResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = (PsiClass)resolveResult.getElement();
      if (aClass != null) {
        PsiManager manager = aClass.getManager();
        PsiTypeParameter[] typeParams = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
        for (PsiTypeParameter typeParam : typeParams) {
          PsiType substituted = resolveResult.getSubstitutor().substitute(typeParam);
          if (substituted instanceof PsiWildcardType) {
            substituted = ((PsiWildcardType)substituted).getBound();
            if (substituted == null) substituted = PsiType.getJavaLangObject(manager, aClass.getResolveScope());
          }
          map.put(typeParam, substituted);
        }

        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        type = factory.createType(aClass, substitutor);
      }
    }
    else if (type instanceof PsiArrayType) {
      return eliminateWildcardsInner(((PsiArrayType)type).getComponentType(), false).createArrayType();
    }
    else if (type instanceof PsiWildcardType) {
      return ((PsiWildcardType)type).getExtendsBound();
    }
    return type;
  }

  public static String[] getUnresolvedReferences(final PsiElement parentOfType, final boolean referenceOnMethod) {
    if (parentOfType != null && parentOfType.getTextLength() > MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED) return ArrayUtil.EMPTY_STRING_ARRAY;
    final List<String> unresolvedRefs = new ArrayList<String>();

    if (parentOfType != null) {
      parentOfType.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression reference) {
          final PsiElement parent = reference.getParent();
          if (parent instanceof PsiReference) return;
          if (referenceOnMethod && parent instanceof PsiMethodCallExpression &&
              reference == ((PsiMethodCallExpression)parent).getMethodExpression()) {
            if (reference.resolve() == null && reference.getReferenceName() != null) unresolvedRefs.add(reference.getReferenceName());
          }
          else if (!referenceOnMethod && !(parent instanceof PsiMethodCallExpression) &&reference.resolve() == null && reference.getReferenceName() != null) {
            unresolvedRefs.add(reference.getReferenceName());
          }
        }
      });
    }
    return unresolvedRefs.toArray(new String[unresolvedRefs.size()]);
  }

  public static String[] getOverides(final PsiClass parent, final PsiType typeByPsiElement) {
    final List<String> overides = new ArrayList<String>();
    final Collection<CandidateInfo> methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(parent, true);
    for (final CandidateInfo candidateInfo : methodsToOverrideImplement) {
      final PsiElement element = candidateInfo.getElement();
      if (typeByPsiElement == PsiUtil.getTypeByPsiElement(element) && element instanceof PsiNamedElement) {
        overides
          .add(((PsiNamedElement)element).getName());
      }
    }
    return overides.toArray(new String[overides.size()]);
  }

  public static String[] getImplements(final PsiClass parent, final PsiType typeByPsiElement) {
    final List<String> overides = new ArrayList<String>();
    final Collection<CandidateInfo> methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(parent, false);
    for (final CandidateInfo candidateInfo : methodsToOverrideImplement) {
      final PsiElement element = candidateInfo.getElement();
      if (typeByPsiElement == PsiUtil.getTypeByPsiElement(element) && element instanceof PsiNamedElement) {
        overides
          .add(((PsiNamedElement)element).getName());
      }
    }
    return overides.toArray(new String[overides.size()]);
  }

  public static String[] getPropertiesHandlersNames(final PsiClass psiClass,
                                                    final boolean staticContext,
                                                    final PsiType varType,
                                                    final PsiElement element) {
    class Change implements Runnable {
      private String[] result;

      public void run() {
        final List<String> propertyHandlers = new ArrayList<String>();
        final PsiField[] fields = psiClass.getFields();

        for (final PsiField field : fields) {
          if (field == element) continue;
          final PsiModifierList modifierList = field.getModifierList();
          if (staticContext && (modifierList != null && !modifierList.hasModifierProperty(PsiModifier.STATIC))) continue;
          final PsiMethod getter = PropertyUtil.generateGetterPrototype(field);
          if (getter.getReturnType().equals(varType) && psiClass.findMethodBySignature(getter, true) == null) {
            propertyHandlers.add(getter.getName());
          }

          final PsiMethod setter = PropertyUtil.generateSetterPrototype(field);
          if (setter.getReturnType().equals(varType) && psiClass.findMethodBySignature(setter, true) == null) {
            propertyHandlers.add(setter.getName());
          }
        }
        result = propertyHandlers.toArray(new String[propertyHandlers.size()]);
      }
    }
    final Change result = new Change();
    element.getManager().performActionWithFormatterDisabled(result);
    return result.result;
  }

  public static boolean isInExcludedPackage(@NotNull final PsiClass psiClass) {
    final String name = psiClass.getQualifiedName();
    if (name == null) return false;
    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    boolean isExcluded = false;
    for (String packages : cis.EXCLUDED_PACKAGES) {
      if (name.startsWith(packages)) {
        isExcluded = true;
      }
    }
    return isExcluded;
  }

  public static boolean isCompletionOfAnnotationMethod(final PsiElement method, final PsiElement place) {
    return method instanceof PsiAnnotationMethod &&
      (place instanceof PsiIdentifier &&
        (place.getParent() instanceof PsiNameValuePair ||
         place.getParent().getParent() instanceof PsiNameValuePair ||
         // @AAA(|A.class)
         ( place.getParent().getParent().getParent() instanceof PsiClassObjectAccessExpression &&
           place.getParent().getParent().getParent().getParent() instanceof PsiNameValuePair
         )
        )
     );
  }

  static boolean isOverwrite(final LookupItem item, final char completionChar) {
    return completionChar != 0
      ? completionChar == Lookup.REPLACE_SELECT_CHAR
      : item.getAttribute(LookupItem.OVERWRITE_ON_AUTOCOMPLETE_ATTR) != null;
  }


  @SuppressWarnings({"unchecked"})
  @NotNull
  public static <T extends PsiType> T originalize(@NotNull T type) {
    return (T)type.accept(new PsiTypeVisitor<PsiType>() {

      public PsiType visitArrayType(final PsiArrayType arrayType) {
        return new PsiArrayType(originalize(arrayType.getComponentType()));
      }

      public PsiType visitCapturedWildcardType(final PsiCapturedWildcardType capturedWildcardType) {
        return PsiCapturedWildcardType.create(originalize(capturedWildcardType.getWildcard()), capturedWildcardType.getContext());
      }

      public PsiType visitClassType(final PsiClassType classType) {
        final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
        final PsiClass psiClass = classResolveResult.getElement();
        final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
        if (psiClass == null) return classType;

        return new PsiImmediateClassType(getOriginalElement(psiClass), originalize(substitutor));
      }

      public PsiType visitEllipsisType(final PsiEllipsisType ellipsisType) {
        return new PsiEllipsisType(originalize(ellipsisType.getComponentType()));
      }

      public PsiType visitPrimitiveType(final PsiPrimitiveType primitiveType) {
        return primitiveType;
      }

      public PsiType visitType(final PsiType type) {
        return type;
      }

      public PsiType visitWildcardType(final PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        final PsiManager manager = wildcardType.getManager();
        if (bound == null) return PsiWildcardType.createUnbounded(manager);
        return wildcardType.isExtends() ? PsiWildcardType.createExtends(manager, bound) : PsiWildcardType.createSuper(manager, bound);
      }
    });
  }

  public static PsiSubstitutor originalize(@Nullable final PsiSubstitutor substitutor) {
    if (substitutor == null) return null;

    PsiSubstitutor originalSubstitutor = PsiSubstitutor.EMPTY;
    for (final Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      final PsiType value = entry.getValue();
      originalSubstitutor = originalSubstitutor.put(getOriginalElement(entry.getKey()), value == null ? null : originalize(value));
    }
    return originalSubstitutor;
  }
}
