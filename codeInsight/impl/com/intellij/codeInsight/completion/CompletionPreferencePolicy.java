
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class CompletionPreferencePolicy implements LookupItemPreferencePolicy{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionPreferencePolicy");

  private final ExpectedTypeInfo[] myExpectedInfos;

  private final TObjectIntHashMap<LookupItem> myItemToIndexMap = new TObjectIntHashMap<LookupItem>();

  private CodeStyleManager myCodeStyleManager;
  private String myPrefix;
  private String myPrefixLowered;
  private String myPrefixCapitals;

  public void setPrefix(String prefix) {
    myPrefix = prefix;
    myPrefixLowered = prefix.toLowerCase();
  }

  @Nullable
  public ExpectedTypeInfo[] getExpectedInfos() {
    return myExpectedInfos;
  }

  public CompletionPreferencePolicy(PsiManager manager, LookupItem[] allItems, ExpectedTypeInfo[] expectedInfos, String prefix) {
    setPrefix( prefix );
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

  public static String capitalsOnly(String s) {
    StringBuffer b = new StringBuffer();
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

  public int compare(final LookupItem item1, final LookupItem item2) {
    if (item1 == item2) return 0;

    String item1StringCap = capitalsOnly(item1.getLookupString());
    String item2StringCap = capitalsOnly(item2.getLookupString());

    if (item1StringCap.startsWith(myPrefixCapitals) && !item2StringCap.startsWith(myPrefixCapitals)) return -1;
    if (!item1StringCap.startsWith(myPrefixCapitals) && item2StringCap.startsWith(myPrefixCapitals)) return 1;

    final String fullCaps = capitalsOnly(myPrefix) + myPrefixCapitals;
    if (item1StringCap.startsWith(fullCaps) && !item2StringCap.startsWith(fullCaps)) return -1;
    if (!item1StringCap.startsWith(fullCaps) && item2StringCap.startsWith(fullCaps)) return 1;
    
    // Check equality in case
    String item1String = item1.getLookupString();
    String item2String = item2.getLookupString();

    if (item1String.startsWith(myPrefix) && !item2String.startsWith(myPrefix)) return -1;
    if (!item1String.startsWith(myPrefix) && item2String.startsWith(myPrefix)) return 1;


    String item1StringLowered = item1.getLookupString().toLowerCase();
    String item2StringLowered = item2.getLookupString().toLowerCase();

    if (item1StringLowered.startsWith(myPrefixLowered) && !item2StringLowered.startsWith(myPrefixLowered)) return -1;
    if (!item1StringLowered.startsWith(myPrefixLowered) && item2StringLowered.startsWith(myPrefixLowered)) return 1;


    Object o1 = item1.getObject();
    Object o2 = item2.getObject();

    if (myExpectedInfos != null) {
      int matchSize1 = getMatchedWordCount(o1);
      int matchSize2 = getMatchedWordCount(o2);
      if (matchSize1 != matchSize2){
        return matchSize2 - matchSize1;
      }
    }

    if (o1 instanceof String || o1 instanceof PsiKeyword) {
      if (o2 instanceof String || o2 instanceof PsiKeyword) {
        return o1.toString().compareTo(o2.toString());
      }
      else {
        return 1;
      }
    }
    if (o2 instanceof String || o2 instanceof PsiKeyword) return -1;

    if (o1 instanceof PsiLocalVariable ||
        o2 instanceof PsiLocalVariable ||
        o1 instanceof PsiParameter ||
        o2 instanceof PsiParameter
    ){
      if (!(o1 instanceof PsiLocalVariable || o1 instanceof PsiParameter)) return 1;
      if (!(o2 instanceof PsiLocalVariable || o2 instanceof PsiParameter)) return -1;
      synchronized(myItemToIndexMap){
        int index1 = myItemToIndexMap.get(item1) - 1;
        if (index1 < 0){
          LOG.error("index1 < 0 : " + item1);
        }
        int index2 = myItemToIndexMap.get(item2) - 1;
        if (index2 < 0){
          LOG.error("index2 < 0 : " + item2);
        }
        return index1 - index2;
      }
    }
    if (o1 instanceof PsiMember && o2 instanceof PsiMember){
      boolean equalsName1 = false;
      if(o1 instanceof PsiNamedElement){
        equalsName1 = myPrefix.equals(((PsiNamedElement)o1).getName());
      }
      boolean equalsName2 = false;
      if(o2 instanceof PsiNamedElement){
        equalsName2 = myPrefix.equals(((PsiNamedElement)o2).getName());
      }

      if(equalsName2){
        if(!equalsName1) return 1;
      }
      else if(equalsName1) return -1;

      PsiType qualifierType1 = CompletionUtil.getQualifierType(item1);
      PsiType qualifierType2 = CompletionUtil.getQualifierType(item2);
      if (qualifierType1 != null && qualifierType2 != null){
        int count1 = StatisticsManager.getInstance().getMemberUseCount(qualifierType1, (PsiMember)o1);
        int count2 = StatisticsManager.getInstance().getMemberUseCount(qualifierType2, (PsiMember)o2);
        if (count2 != count1) {
          return count2 - count1;
        }
      }
    }
    return 0;
  }

  private int getMatchedWordCount(Object o) {
    String name;
    if (o instanceof PsiVariable) {
      name = ((PsiVariable)o).getName();
      VariableKind variableKind = myCodeStyleManager.getVariableKind((PsiVariable)o);
      name = myCodeStyleManager.variableNameToPropertyName(name, variableKind);
    }
    else if (o instanceof PsiMethod) {
      name = ((PsiMethod)o).getName();
    }
    else if (o instanceof PsiClass && myExpectedInfos.length == 1){
      final PsiClass psiClass = (PsiClass)o;
      if ("true".equals(System.getProperty("sort.lookup.items.by.proximity"))) {
        final THashSet<PsiClass> classes = LookupManagerImpl.getFirstClasses(myExpectedInfos);
        if (classes.contains(psiClass)) {
          if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT) || !LookupManagerImpl.hasFewAbstractMethods(psiClass)) return -1;
        }
      }

      final PsiType type = myExpectedInfos[0].getType();
      final PsiType objectType = psiClass.getManager().getElementFactory().createType(psiClass);
      PsiType componentType = type.getDeepComponentType();

      if(type instanceof PsiArrayType && componentType.equals(objectType)){
        return Integer.MAX_VALUE;
      }

      int count = StatisticsManager.getInstance().getMemberUseCount(type, psiClass);
      if(count == 0){
        if(componentType.equals(objectType)){
          return 1;
        }
        else
          return 0;
      }
      return count + 1;
    }
    else
      return 0;

    int max = calcMatch(NameUtil.nameToWords(name), 0);
    max = calcMatch(NameUtil.nameToWords(truncDigits(name)), max);
    return max;
  }

  private int calcMatch(final String[] words, int max) {
    for (ExpectedTypeInfo myExpectedInfo : myExpectedInfos) {
      String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).expectedName;
      if (expectedName == null) continue;
      max = calcMatch(expectedName, words, max);
      max = calcMatch(truncDigits(expectedName), words, max);
    }
    return max;
  }

  private static int calcMatch(final String expectedName, final String[] words, int max) {
    if (expectedName == null) return max;

    String[] expectedWords = NameUtil.nameToWords(expectedName);
    int limit = Math.min(words.length, expectedWords.length);
    for (int i = 0; i < limit; i++) {
      String word = words[words.length - i - 1];
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
    int count = 0;
    while(true){
      char c = name.charAt(name.length() - count - 1);
      if (!Character.isDigit(c)) break;
      count++;
    }
    return name.substring(0, name.length() - count);
  }
}