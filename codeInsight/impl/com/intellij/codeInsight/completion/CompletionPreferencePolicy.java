
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiProximity;
import com.intellij.psi.util.PsiProximityComparator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class CompletionPreferencePolicy implements LookupItemPreferencePolicy{
  private final ExpectedTypeInfo[] myExpectedInfos;

  private final TObjectIntHashMap<LookupItem> myItemToIndexMap = new TObjectIntHashMap<LookupItem>();

  private final PsiProximityComparator myProximityComparator;
  private final CodeStyleManager myCodeStyleManager;
  private String myPrefix;
  private String myPrefixLowered;
  private final String myPrefixCapitals;
  private final PsiElement myPosition;
  private NullableLazyValue<PsiMethod> myPositionMethod = new NullableLazyValue<PsiMethod>() {
    protected PsiMethod compute() {
      //final PsiFile file = myPosition.getContainingFile().getOriginalFile();
      //PsiTreeUtil.findElementOfClassAtOffset(file, myPosition.getTextRange().getStartOffset(), PsiMethod.class, false)

      return PsiTreeUtil.getParentOfType(myPosition, PsiMethod.class, false);
    }
  };

  public CompletionPreferencePolicy(PsiManager manager, LookupItem[] allItems, ExpectedTypeInfo[] expectedInfos, String prefix, @NotNull PsiElement position) {
    myPosition = position;
    setPrefix( prefix );
    myProximityComparator = new PsiProximityComparator(position, manager.getProject());
    myPrefixCapitals = capitalsOnly(prefix);
    myCodeStyleManager = manager.getCodeStyleManager();
    if(expectedInfos != null){
      final Map<PsiType, ExpectedTypeInfo> map = new HashMap<PsiType, ExpectedTypeInfo>(expectedInfos.length);
      for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
        if (!map.containsKey(expectedInfo.getType())) {
          map.put(expectedInfo.getType(), expectedInfo);
        }
      }
      myExpectedInfos = map.values().toArray(new ExpectedTypeInfo[map.size()]);
    }
    else myExpectedInfos = null;
    synchronized(myItemToIndexMap){
      for(int i = 0; i < allItems.length; i++){
        myItemToIndexMap.put(allItems[i], i + 1);
      }
    }
  }

  public void setPrefix(String prefix) {
    myPrefix = prefix;
    myPrefixLowered = prefix.toLowerCase();
  }

  @Nullable
  public ExpectedTypeInfo[] getExpectedInfos() {
    return myExpectedInfos;
  }

  private static String capitalsOnly(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (Character.isUpperCase(s.charAt(i))) {
        b.append(s.charAt(i));
      }
    }

    return b.toString();
  }

  public void itemSelected(LookupItem item) {
    final Object o = item.getObject();
    if (o instanceof PsiMember){
      final PsiType qualifierType = CompletionUtil.getQualifierType(item);
      if (qualifierType != null){
        StatisticsManager.getInstance().incMemberUseCount(qualifierType, (PsiMember)o);
      }
    }
  }

  public int[] getWeight(final LookupItem<?> item) {
    if (item.getAttribute(LookupItem.WEIGHT) != null) return item.getAttribute(LookupItem.WEIGHT);

    final int[] result = new int[13];

    String item1StringCap = capitalsOnly(item.getLookupString());
    result[0] = item1StringCap.startsWith(myPrefixCapitals) ? 1 : 0;

    final Object object = item.getObject();
    if (object instanceof PsiClass && myExpectedInfos != null) {
      final PsiClass psiClass = (PsiClass)object;
      for (final ExpectedTypeInfo info : myExpectedInfos) {
        final PsiClassType psiClassType = psiClass.getManager().getElementFactory().createType(psiClass);
        if(info.getType().getDeepComponentType().equals(psiClassType)) {
          result[0] = Integer.MAX_VALUE;
          break;
        }
        else if(info.getDefaultType().getDeepComponentType().equals(psiClassType)) {
          result[0] = Integer.MAX_VALUE - 1;
          break;
        }
      }
    }

    result[1] = item1StringCap.startsWith(capitalsOnly(myPrefix) + myPrefixCapitals) ? 1 : 0;
    result[2] = item.getLookupString().startsWith(myPrefix) ? 1 : 0;
    result[3] = item.getLookupString().toLowerCase().startsWith(myPrefixLowered) ? 1 : 0;
    result[4] = getExpectedTypeMatchingDegree(object, item);


    result[5] = object instanceof PsiLocalVariable || object instanceof PsiParameter ? 2 : object instanceof PsiEnumConstant ? 1 : 0;

    result[6] = getRecursiveCallWeight(object);

    final String name = getName(object);
    final int nameEndMatchingDegree = getNameEndMatchingDegree(object, name);
    result[7] = nameEndMatchingDegree;

    if (object instanceof PsiMember) {
      final PsiType qualifierType1 = CompletionUtil.getQualifierType(item);
      if (qualifierType1 != null){
        result[8] = StatisticsManager.getInstance().getMemberUseCount(qualifierType1, (PsiMember)object);
      }
    }

    if (object instanceof PsiElement) {
      final PsiProximity proximity = myProximityComparator.getProximity((PsiElement)object);
      result[9] = proximity ==null ? -1 : 239 - proximity.ordinal();
    }

    if (name != null && myExpectedInfos != null) {
      int max = 0;
      final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(truncDigits(name));
      for (ExpectedTypeInfo myExpectedInfo : myExpectedInfos) {
        String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).expectedName;
        if (expectedName != null) {
          final THashSet<String> set = new THashSet<String>(NameUtil.nameToWordsLowerCase(truncDigits(expectedName)));
          set.retainAll(wordsNoDigits);
          max = Math.max(max, set.size());
        }
      }
      result[10] = max;
    }

    if (object instanceof PsiLocalVariable || object instanceof PsiParameter) {
      result[11] = 5;
    }
    else if (object instanceof PsiField) {
      result[11] = 4;
    }
    else if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;
      result[11]= PropertyUtil.isSimplePropertyGetter(method) ? 3 : 2;
    }

    if (name != null && myExpectedInfos != null && nameEndMatchingDegree != 0) {
      result[12] = 239 - NameUtil.nameToWords(name).length;
    }

    item.setAttribute(LookupItem.WEIGHT, result);

    return result;
  }

  private int getNameEndMatchingDegree(final Object object, final String name) {
    int res = 0;
    if (name != null && myExpectedInfos != null) {
      if (myPrefix.equals(name)) {
        res = Integer.MAX_VALUE;
      } else {
        final List<String> words = NameUtil.nameToWordsLowerCase(name);
        final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(truncDigits(name));
        int max1 = calcMatch(words, 0);
        max1 = calcMatch(wordsNoDigits, max1);
        res = max1;
      }
    }

    if (object instanceof String) res = 1;
    else if (object instanceof PsiKeyword) res = -1;
    return res;
  }

  public int compare(final LookupItem item1, final LookupItem item2) {
    if (item1 == item2) return 0;

    if (LookupManagerImpl.isUseNewSorting()) {
      if (item2.getAttribute(LookupItem.DONT_PREFER) != null) return -1;
      return 0;
    }

    return doCompare(item1.getPriority(), item2.getPriority(), getWeight(item1), getWeight(item2));
  }

  public static int doCompare(final double priority1, final double priority2, final int[] weight1, final int[] weight2) {
    if (priority1 != priority2) {
      final double v = priority1 - priority2;
      if (v > 0) return -1;
      if (v < 0) return 1;
    }

    for (int i = 0; i < weight1.length; i++) {
      final int w1 = weight1[i];
      final int w2 = weight2[i];
      if (w2 > w1) return 1;
      if (w2 < w1) return -1;
    }

    return 0;
  }

  private int getRecursiveCallWeight(Object o) {
    if (myExpectedInfos != null) {
      final PsiType itemType = getPsiType(o);
      if (itemType != null) {
        final PsiMethod positionMethod = myPositionMethod.getValue();
        if (positionMethod != null) {
          for (final ExpectedTypeInfo expectedInfo : myExpectedInfos) {
            if (expectedInfo.getType().isAssignableFrom(itemType) && expectedInfo.getCalledMethod() == positionMethod) {
              return 0;
            }
          }
        }
      }
    }
    return 1;
  }

  private int getExpectedTypeMatchingDegree(Object o, final LookupItem item) {
    if (myExpectedInfos == null) return 0;
    int delta = 0;

    final PsiType itemType = getPsiType(o);
    if (itemType != null) {
      for (final ExpectedTypeInfo expectedInfo : myExpectedInfos) {
        final PsiType defaultType = expectedInfo.getDefaultType();
        if (defaultType != expectedInfo.getType() && defaultType.isAssignableFrom(itemType)) {
          delta = 1;
          break;
        }
      }
    }

    if (o instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)o;

      PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
      if (substitutor != null) {
        final PsiType type = substitutor.substitute(method.getReturnType());
        if (type instanceof PsiClassType && ((PsiClassType) type).resolve() instanceof PsiTypeParameter) return -1;
      }

      if (!method.getManager().getResolveHelper().isAccessible(method, myPosition, null)) return -2;

    }
    else if (o instanceof PsiClass && myExpectedInfos.length == 1) {
      final PsiClass psiClass = (PsiClass)o;
      final PsiType type = myExpectedInfos[0].getType();
      final PsiType objectType = psiClass.getManager().getElementFactory().createType(psiClass);
      PsiType componentType = type.getDeepComponentType();

      if(type instanceof PsiArrayType && componentType.equals(objectType)){
        return Integer.MAX_VALUE;
      }

      int count = StatisticsManager.getInstance().getMemberUseCount(type, psiClass);
      if(count == 0){
        return componentType.equals(objectType) ? delta + 1 : delta;
      }
      return delta + count + 1;
    }

    return delta;
  }

  @Nullable private static PsiType getPsiType(final Object o) {
    if (o instanceof PsiVariable) {
      return ((PsiVariable)o).getType();
    }
    else if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getReturnType();
    }
    else if (o instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)o;
      return psiClass.getManager().getElementFactory().createType(psiClass);
    }
    else if (o instanceof PsiExpression) {
      return ((PsiExpression)o).getType();
    }
    return null;
  }


  @Nullable
  private String getName(Object o) {
    if (o instanceof PsiVariable) {
      String name = ((PsiVariable)o).getName();
      VariableKind variableKind = myCodeStyleManager.getVariableKind((PsiVariable)o);
      return myCodeStyleManager.variableNameToPropertyName(name, variableKind);
    }
    else if (o instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)o;
      return method.getName();
    }
    return null;
  }

  private int getMatchedWordCount(@NotNull  String name) {
    int max = calcMatch(NameUtil.nameToWordsLowerCase(name), 0);
    max = calcMatch(NameUtil.nameToWordsLowerCase(truncDigits(name)), max);
    return max;
  }

  private int calcMatch(final List<String> words, int max) {
    for (ExpectedTypeInfo myExpectedInfo : myExpectedInfos) {
      String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).expectedName;
      if (expectedName == null) continue;
      max = calcMatch(expectedName, words, max);
      max = calcMatch(truncDigits(expectedName), words, max);
    }
    return max;
  }

  private static int calcMatch(final String expectedName, final List<String> words, int max) {
    if (expectedName == null) return max;

    String[] expectedWords = NameUtil.nameToWords(expectedName);
    int limit = Math.min(words.size(), expectedWords.length);
    for (int i = 0; i < limit; i++) {
      String word = words.get(words.size() - i - 1);
      String expectedWord = expectedWords[expectedWords.length - i - 1];
      if (word.equalsIgnoreCase(expectedWord)) {
        max = Math.max(max, i + 1);
      }
      else {
        break;
      }
    }
    return max;
  }

  private static String truncDigits(String name){
    int count = name.length() - 1;
    while (count >= 0) {
      char c = name.charAt(count);
      if (!Character.isDigit(c)) break;
      count--;
    }
    return name.substring(0, count + 1);
  }

}