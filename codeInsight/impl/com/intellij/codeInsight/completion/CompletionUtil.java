package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.position.SuperParentFilter;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Compiler;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompletionUtil {
  public static final Key TAIL_TYPE_ATTR = Key.create("tailType"); // one of constants defined in TailType interface

  private static final Key<SmartPsiElementPointer> QUALIFIER_TYPE_ATTR =
    Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
  private static final CompletionData ourJavaCompletionData = new JavaCompletionData();
  private static final CompletionData ourJava15CompletionData = new Java15CompletionData();
  private static final CompletionData ourJSPCompletionData = new JSPCompletionData();
  private static final CompletionData ourXmlCompletionData = new XmlCompletionData();
  private static final CompletionData ourGenericCompletionData = new CompletionData() {
    {
      final CompletionVariant variant = new CompletionVariant(PsiElement.class, TrueFilter.INSTANCE);
      variant.addCompletionFilter(TrueFilter.INSTANCE, TailType.NONE);
      registerVariant(variant);
    }
  };
  private static final CompletionData ourWordCompletionData = new WordCompletionData();
  private static final CompletionData ourJavaDocCompletionData = new JavaDocCompletionData();

  static {
    registerCompletionData(StdFileTypes.JSPX, new JspxCompletionData());
  }

  private static HashMap<FileType, CompletionData> completionDatas;
  public static final Key<CompletionData> ENFORCE_COMPLETION_DATA_KEY = new Key<CompletionData>("Enforce completionData");
  private static final int MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED = 50000;
  @NonNls
  public static final String GET_PREFIX = "get";
  @NonNls
  public static final String SET_PREFIX = "set";
  @NonNls
  public static final String IS_PREFIX = "is";

  private static void initBuiltInCompletionDatas() {
    completionDatas = new HashMap<FileType, CompletionData>();
    registerCompletionData(StdFileTypes.HTML, new HtmlCompletionData());
    registerCompletionData(StdFileTypes.XHTML, new XHtmlCompletionData());
  }

  public static final @NonNls String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz ";
  public static final @NonNls String DUMMY_IDENTIFIER_TRIMMED = DUMMY_IDENTIFIER.trim();
  public static final Key<String> COMPLETION_PREFIX = Key.create("Completion prefix");
  public static final Key<PsiElement> COPY_KEY = Key.create("COPY_KEY");

  public static PsiType getQualifierType(LookupItem item) {
    return (PsiType)item.getAttribute(QUALIFIER_TYPE_ATTR);
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
      Object o = item.getObject();
      PsiType qualifierType = getQualifierType(item);
      if (qualifierType == null) continue;
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
  }

  public static CompletionData getCompletionDataByElement(PsiElement element, CompletionContext context) {
    CompletionData wordCompletionData = null;
    ASTNode textContainer = element != null ? element.getNode() : null;
    while (textContainer != null) {
      final IElementType elementType = textContainer.getElementType();
      final TokenSet readableTextContainerElements = elementType.getLanguage().getReadableTextContainerElements();
      if (readableTextContainerElements.contains(elementType) || elementType == ElementType.PLAIN_TEXT) {
        wordCompletionData = ourWordCompletionData;
      }
      textContainer = textContainer.getTreeParent();
    }
    final CompletionData completionDataByElementInner = getCompletionDataByElementInner(element, context);
    if (wordCompletionData != null) return new CompositeCompletionData(completionDataByElementInner, wordCompletionData);
    return completionDataByElementInner;
  }

  public static CompletionData getCompletionDataByElementInner(PsiElement element, CompletionContext context) {
    final PsiFile file = context.file;
    if (context.file.getUserData(ENFORCE_COMPLETION_DATA_KEY) != null) {
      return context.file.getUserData(ENFORCE_COMPLETION_DATA_KEY);
    }
    final CompletionData completionDataByFileType = getCompletionDataByFileType(file.getFileType());
    if (completionDataByFileType != null) return completionDataByFileType;

    if ((file instanceof PsiJavaFile || file instanceof PsiCodeFragment) &&
        !(PsiUtil.isInJspFile(file)) // TODO: we need to check the java context of JspX
      ) {
      if (element != null && new SuperParentFilter(new ClassFilter(PsiDocComment.class)).isAcceptable(element, element.getParent())) {
        return ourJavaDocCompletionData;
      }
      else {
        return element != null && PsiUtil.getLanguageLevel(element).equals(LanguageLevel.JDK_1_5)
               ? ourJava15CompletionData
               : ourJavaCompletionData;
      }
    }
    else if (file.getViewProvider().getBaseLanguage() == StdLanguages.JSP) {
      return ourJSPCompletionData;
    }
    else if (file.getViewProvider().getBaseLanguage() == StdLanguages.XML) {
      return ourXmlCompletionData;
    }
    return ourGenericCompletionData;
  }

  public static void registerCompletionData(FileType fileType, CompletionData completionData) {
    if (completionDatas == null) initBuiltInCompletionDatas();
    completionDatas.put(fileType, completionData);
  }

  public static CompletionData getCompletionDataByFileType(FileType fileType) {
    if (completionDatas == null) initBuiltInCompletionDatas();
    return completionDatas.get(fileType);
  }

  public static boolean checkName(String name, String prefix) {
    return checkName(name, prefix, false);
  }

  public static boolean checkName(String name, String prefix, boolean forceCaseInsensitive) {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (name == null) {
      return false;
    }
    boolean ret = true;
    if (prefix != null) {
      int variant = settings.COMPLETION_CASE_SENSITIVE;
      variant = forceCaseInsensitive ? CodeInsightSettings.NONE : variant;

      switch (variant) {
        case CodeInsightSettings.NONE:
          ret = name.toLowerCase().startsWith(prefix.toLowerCase());
          break;
        case CodeInsightSettings.FIRST_LETTER:
          if (name.length() > 0) {
            if (prefix.length() > 0) {
              ret = name.charAt(0) == prefix.charAt(0) && name.toLowerCase().startsWith(prefix.substring(1).toLowerCase(), 1);
            }
          }
          else {
            if (prefix.length() > 0) ret = false;
          }

          break;
        case CodeInsightSettings.ALL:
          ret = name.startsWith(prefix, 0);
          break;
        default:
          ret = false;
      }
    }
    return ret;
  }


  public static Pattern createCampelHumpsMatcher(String pattern) {
    Pattern pat = null;
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    int variant = settings.COMPLETION_CASE_SENSITIVE;
    Perl5Compiler compiler = new Perl5Compiler();

    try {
      switch (variant) {
        case CodeInsightSettings.NONE:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 0, false));
          break;
        case CodeInsightSettings.FIRST_LETTER:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 1, false));
          break;
        case CodeInsightSettings.ALL:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 0, true));
          break;
        default:
          pat = compiler.compile(NameUtil.buildRegexp(pattern, 0, false));
      }
    }
    catch (MalformedPatternException me) {
    }
    return pat;
  }


  public static LookupItemPreferencePolicy completeVariableNameForRefactoring(Project project,
                                                                              Set<LookupItem> set,
                                                                              String prefix,
                                                                              PsiType varType,
                                                                              VariableKind varKind) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    CodeStyleManagerEx codeStyleManager = (CodeStyleManagerEx)CodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    final String[] suggestedNames = suggestedNameInfo.names;
    LookupItemUtil.addLookupItems(set, suggestedNames, prefix);

    if (set.isEmpty() && PsiType.VOID != varType) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
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

      LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, prefix);
    }

    return new NamePreferencePolicy(suggestedNameInfo);
  }

  public static String[] getOverlappedNameVersions(final String prefix, final String[] suggestedNames, String suffix) {
    final List<String> newSuggestions = new ArrayList<String>();
    int longestOverlap = 0;

    for (String suggestedName : suggestedNames) {
      if (suggestedName.toUpperCase().startsWith(prefix.toUpperCase())) {
        newSuggestions.add(suggestedName);
        longestOverlap = prefix.length();
      }

      suggestedName = "" + Character.toUpperCase(suggestedName.charAt(0)) + suggestedName.substring(1);
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

        PsiElementFactory factory = manager.getElementFactory();
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
      parentOfType.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression reference) {
          final PsiElement parent = reference.getParent();
          if (parent instanceof PsiReference) return;
          if (referenceOnMethod && parent instanceof PsiMethodCallExpression &&
              reference == ((PsiMethodCallExpression)parent).getMethodExpression()) {
            if (reference.resolve() == null && reference.getReferenceName() != null) unresolvedRefs.add(reference.getReferenceName());
          }
          else if (!referenceOnMethod && reference.resolve() == null && reference.getReferenceName() != null) {
            unresolvedRefs.add(reference.getReferenceName());
          }
        }
      });
    }
    return unresolvedRefs.toArray(new String[unresolvedRefs.size()]);
  }

  public static String[] getOverides(final PsiClass parent, final PsiType typeByPsiElement) {
    final List<String> overides = new ArrayList<String>();
    final CandidateInfo[] methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(parent, true);
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
    final CandidateInfo[] methodsToOverrideImplement = OverrideImplementUtil.getMethodsToOverrideImplement(parent, false);
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

  public static boolean isInExcludedPackage(final PsiClass psiClass) {
    final String name = psiClass.getQualifiedName();
    if (name == null) return false;
    CodeInsightSettings cis = CodeInsightSettings.getInstance();
    boolean isExcluded = false;
    //noinspection ForLoopReplaceableByForEach
    for(int i=0; i<cis.EXCLUDED_PACKAGES.length; i++) {
      if (name.startsWith(cis.EXCLUDED_PACKAGES [i])) {
        isExcluded = true;
      }
    }
    if (isExcluded) return true;
    return false;
  }
}
