// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.psi.*;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Bas Leijdekkers
 */
class RegExpEquivalenceChecker {

  private static final Comparator<PsiElement> TEXT_COMPARATOR = Comparator.comparing(PsiElement::getText);

  private RegExpEquivalenceChecker() {}

  public static boolean areElementsEquivalent(RegExpElement element1, RegExpElement element2) {
    if (element1 == null) {
      return element2 == null;
    }
    if (element1.getClass() != element2.getClass()) {
      return false;
    }
    if (element1 instanceof RegExpChar) {
      return areCharsEquivalent((RegExpChar)element1, (RegExpChar)element2);
    }
    else if (element1 instanceof RegExpBranch) {
      return areBranchesEquivalent((RegExpBranch)element1, (RegExpBranch)element2);
    }
    else if (element1 instanceof RegExpClass) {
      return areClassesEquivalent((RegExpClass)element1, (RegExpClass)element2);
    }
    else if (element1 instanceof RegExpCharRange) {
      return areCharRangesEquivalent((RegExpCharRange)element1, (RegExpCharRange)element2);
    }
    else if (element1 instanceof RegExpClosure) {
      return areClosuresEquivalent((RegExpClosure)element1, (RegExpClosure)element2);
    }
    else if (element1 instanceof RegExpGroup) {
      return areGroupsEquivalent((RegExpGroup)element1, (RegExpGroup)element2);
    }
    else if (element1 instanceof RegExpIntersection) {
      return areIntersectionsEquivalent((RegExpIntersection)element1, (RegExpIntersection)element2);
    }
    else if (element1 instanceof RegExpNamedGroupRef) {
      return areNamedGroupRefsEquivalent((RegExpNamedGroupRef)element1, (RegExpNamedGroupRef)element2);
    }
    else if (element1 instanceof RegExpNumber) {
      return areNumbersEquivalent((RegExpNumber)element1, (RegExpNumber)element2);
    }
    else if (element1 instanceof RegExpOptions) {
      return areOptionsEquivalent((RegExpOptions)element1, (RegExpOptions)element2);
    }
    else if (element1 instanceof RegExpPattern) {
      return arePatternsEquivalent((RegExpPattern)element1, (RegExpPattern)element2);
    }
    else if (element1 instanceof RegExpSetOptions) {
      return areSetOptionsEquivalent((RegExpSetOptions)element1, (RegExpSetOptions)element2);
    }
    return element1.textMatches(element2);
  }

  private static boolean areSetOptionsEquivalent(RegExpSetOptions setOptions1, RegExpSetOptions setOptions2) {
    return areOptionsEquivalent(setOptions1.getOnOptions(), setOptions2.getOnOptions()) &&
           areOptionsEquivalent(setOptions1.getOffOptions(), setOptions2.getOffOptions());
  }

  private static boolean areOptionsEquivalent(RegExpOptions options1, RegExpOptions options2) {
    if (options1 == null) {
      return options2 == null;
    }
    else if (options2 == null) {
      return false;
    }
    return StringUtil.containsAnyChar(options1.getText(), options2.getText());
  }

  private static boolean areNamedGroupRefsEquivalent(RegExpNamedGroupRef namedGroupRef1, RegExpNamedGroupRef namedGroupRef2) {
    final String name = namedGroupRef1.getGroupName();
    return name != null && name.equals(namedGroupRef2.getGroupName());
  }

  private static boolean areIntersectionsEquivalent(RegExpIntersection intersection1, RegExpIntersection intersection2) {
    final RegExpClassElement[] operands1 = intersection1.getOperands();
    final RegExpClassElement[] operands2 = intersection2.getOperands();
    if (operands1.length != operands2.length) {
      return false;
    }
    Arrays.sort(operands1, TEXT_COMPARATOR);
    Arrays.sort(operands2, TEXT_COMPARATOR);
    for (int i = 0; i < operands1.length; i++) {
      if (!areElementsEquivalent(operands1[i], operands2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean areGroupsEquivalent(RegExpGroup group1, RegExpGroup group2) {
    return group1.getType() == group2.getType() && arePatternsEquivalent(group1.getPattern(), group2.getPattern());
  }

  private static boolean arePatternsEquivalent(RegExpPattern pattern1, RegExpPattern pattern2) {
    if (pattern1 == null) return pattern2 == null;
    if (pattern2 == null) return false;
    final RegExpBranch[] branches1 = pattern1.getBranches();
    final RegExpBranch[] branches2 = pattern2.getBranches();
    if (branches1.length != branches2.length) {
      return false;
    }
    Arrays.sort(branches1, TEXT_COMPARATOR);
    Arrays.sort(branches2, TEXT_COMPARATOR);
    for (int i = 0; i < branches1.length; i++) {
      if (!areBranchesEquivalent(branches1[i], branches2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean areClosuresEquivalent(RegExpClosure element1, RegExpClosure element2) {
    return areElementsEquivalent(element1.getAtom(), element2.getAtom()) &&
      areQuantifiersEquivalent(element1.getQuantifier(), element2.getQuantifier());
  }

  private static boolean areQuantifiersEquivalent(RegExpQuantifier quantifier1, RegExpQuantifier quantifier2) {
    if (quantifier1.isCounted()) {
      return quantifier2.isCounted() &&
             areNumbersEquivalent(quantifier1.getMin(), quantifier2.getMin()) &&
             areNumbersEquivalent(quantifier1.getMax(), quantifier2.getMax());
    }
    return quantifier1.textMatches(quantifier2);
  }

  private static boolean areNumbersEquivalent(RegExpNumber number1, RegExpNumber number2) {
    if (number1 == null) {
      return number2 == null;
    }
    else if (number2 == null) {
      return false;
    }
    final Number value1 = number1.getValue();
    final Number value2 = number2.getValue();
    return value1 != null && value1.equals(value2);
  }

  private static boolean areCharRangesEquivalent(RegExpCharRange charRange1, RegExpCharRange charRange2) {
    return areCharsEquivalent(charRange1.getFrom(), charRange2.getFrom()) &&
           areCharsEquivalent(charRange1.getTo(), charRange2.getTo());
  }

  private static boolean areClassesEquivalent(RegExpClass aClass1, RegExpClass aClass2) {
    if (aClass1.isNegated() != aClass2.isNegated()) {
      return false;
    }
    final RegExpClassElement[] elements1 = aClass1.getElements();
    final RegExpClassElement[] elements2 = aClass2.getElements();
    if (elements1.length != elements2.length) {
      return false;
    }
    Arrays.sort(elements1, TEXT_COMPARATOR);
    Arrays.sort(elements2, TEXT_COMPARATOR);
    for (int i = 0; i < elements1.length; i++){
      if (!areElementsEquivalent(elements1[i], elements2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean areBranchesEquivalent(RegExpBranch branch1, RegExpBranch branch2) {
    final RegExpAtom[] atoms1 = branch1.getAtoms();
    final RegExpAtom[] atoms2 = branch2.getAtoms();
    if (atoms1.length != atoms2.length) {
      return false;
    }
    for (int i = 0; i < atoms1.length; i++) {
      if (!areElementsEquivalent(atoms1[i], atoms2[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean areCharsEquivalent(RegExpChar aChar1, RegExpChar aChar2) {
    return aChar1 == null ? aChar2 == null : aChar2 != null && aChar1.getValue() == aChar2.getValue();
  }
}
