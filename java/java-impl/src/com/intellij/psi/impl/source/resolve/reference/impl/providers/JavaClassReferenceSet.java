/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
*/
public class JavaClassReferenceSet {
  public static final char DOT = '.';
  public static final char DOLLAR = '$';
  public static final char LT = '<';
  public static final char GT = '>';
  public static final char COMMA = ',';
  public static final char QUESTION = '?';
  public static final String EXTENDS = "extends";
  public static final String SUPER = "super";

  private JavaClassReference[] myReferences;
  private List<JavaClassReferenceSet> myNestedGenericParameterReferences;
  private JavaClassReferenceSet myContext;
  private PsiElement myElement;
  private final int myStartInElement;
  private final JavaClassReferenceProvider myProvider;

  public JavaClassReferenceSet(@NotNull String str, @NotNull PsiElement element, int startInElement, final boolean isStatic, @NotNull JavaClassReferenceProvider provider) {
    this(str, element, startInElement, isStatic, provider, null);
  }

  private JavaClassReferenceSet(@NotNull String str, @NotNull PsiElement element, int startInElement, final boolean isStatic, @NotNull JavaClassReferenceProvider provider,
                        JavaClassReferenceSet context) {
    myStartInElement = startInElement;
    myProvider = provider;
    reparse(str, element, isStatic, context);
  }

  @NotNull
  public JavaClassReferenceProvider getProvider() {
    return myProvider;
  }

  @NotNull
  public TextRange getRangeInElement() {
    PsiReference[] references = getReferences();
    return new TextRange(references[0].getRangeInElement().getStartOffset(), references[references.length - 1].getRangeInElement().getEndOffset());
  }

  private void reparse(@NotNull String str, @NotNull PsiElement element, final boolean isStaticImport, JavaClassReferenceSet context) {
    myElement = element;
    myContext = context;
    List<JavaClassReference> referencesList = new ArrayList<>();
    int currentDot = -1;
    int referenceIndex = 0;
    boolean allowDollarInNames = isAllowDollarInNames();
    boolean allowSpaces = isAllowSpaces();
    boolean allowGenerics = false;
    boolean allowWildCards = JavaClassReferenceProvider.ALLOW_WILDCARDS.getBooleanValue(getOptions());
    boolean allowGenericsCalculated = false;
    boolean parsingClassNames = true;

    while (parsingClassNames) {
      int nextDotOrDollar = -1;
      for (int curIndex = currentDot + 1; curIndex < str.length(); ++curIndex) {
        char ch = str.charAt(curIndex);

        if (ch == DOT || ch == DOLLAR && allowDollarInNames) {
          nextDotOrDollar = curIndex;
          break;
        }

        if (ch == LT || ch == COMMA) {
          if (!allowGenericsCalculated) {
            allowGenerics = !isStaticImport && PsiUtil.isLanguageLevel5OrHigher(element);
            allowGenericsCalculated = true;
          }

          if (allowGenerics) {
            nextDotOrDollar = curIndex;
            break;
          }
        }
      }

      if (nextDotOrDollar == -1) {
        nextDotOrDollar = currentDot + 1;
        for (int i = nextDotOrDollar; i < str.length() && Character.isJavaIdentifierPart(str.charAt(i)); ++i) nextDotOrDollar++;
        parsingClassNames = false;
        int j = skipSpaces(nextDotOrDollar, str.length(), str, allowSpaces);

        if (j < str.length()) {
          char ch = str.charAt(j);
          boolean recognized = false;

          if (ch == '[') {
            j = skipSpaces(j + 1, str.length(), str, allowSpaces);
            if (j < str.length() && str.charAt(j) == ']') {
              j = skipSpaces(j + 1, str.length(), str, allowSpaces);
              recognized = j == str.length();
            }
          }

          Boolean aBoolean = JavaClassReferenceProvider.JVM_FORMAT.getValue(getOptions());
          if (!recognized && (aBoolean == null || !aBoolean.booleanValue())) {
            nextDotOrDollar = -1; // abort resolve
          }
        }
      }

      if (nextDotOrDollar != -1 && nextDotOrDollar < str.length()) {
        char c = str.charAt(nextDotOrDollar);
        if (c == LT) {
          boolean recognized = false;
          int start = skipSpaces(nextDotOrDollar + 1, str.length(), str, allowSpaces);
          int j = str.lastIndexOf(GT);
          int end = skipSpacesBackward(j, 0, str, allowSpaces);
          if (end != -1 && end > start) {
            if (myNestedGenericParameterReferences == null) myNestedGenericParameterReferences = new ArrayList<>(1);
            myNestedGenericParameterReferences.add(new JavaClassReferenceSet(
              str.substring(start, end), myElement, myStartInElement + start, isStaticImport, myProvider, this));
            parsingClassNames = false;
            j = skipSpaces(j + 1, str.length(), str, allowSpaces);
            recognized = j == str.length();
          }
          if (!recognized) {
            nextDotOrDollar = -1; // abort resolve
          }
        }
        else if (c == COMMA && myContext != null) {
          if (myContext.myNestedGenericParameterReferences == null) myContext.myNestedGenericParameterReferences = new ArrayList<>(1);
          int start = skipSpaces(nextDotOrDollar + 1, str.length(), str, allowSpaces);
          myContext.myNestedGenericParameterReferences.add(new JavaClassReferenceSet(
            str.substring(start), myElement, myStartInElement + start, isStaticImport, myProvider, this));
          parsingClassNames = false;
        }
      }

      int maxIndex = nextDotOrDollar > 0 ? nextDotOrDollar : str.length();
      int beginIndex = skipSpaces(currentDot + 1, maxIndex, str, allowSpaces);
      int endIndex = skipSpacesBackward(maxIndex, beginIndex, str, allowSpaces);
      boolean skipReference = false;
      if (allowWildCards && str.charAt(beginIndex) == QUESTION) {
        int next = skipSpaces(beginIndex + 1, endIndex, str, allowSpaces);
        if (next != beginIndex + 1) {
          String keyword = str.startsWith(EXTENDS, next) ? EXTENDS : str.startsWith(SUPER, next) ? SUPER : null;
          if (keyword != null) {
            next = skipSpaces(next + keyword.length(), endIndex, str, allowSpaces);
            beginIndex = next;
          }
        }
        else if (endIndex == beginIndex + 1) {
          skipReference = true;
        }
      }
      if (!skipReference) {
        TextRange textRange = TextRange.create(myStartInElement + beginIndex, myStartInElement + endIndex);
        JavaClassReference currentContextRef = createReference(
          referenceIndex, str.substring(beginIndex, endIndex), textRange, isStaticImport);
        referenceIndex++;
        referencesList.add(currentContextRef);
      }
      if ((currentDot = nextDotOrDollar) < 0) {
        break;
      }
    }

    myReferences = referencesList.toArray(new JavaClassReference[referencesList.size()]);
  }

  private static int skipSpaces(int pos, int max, @NotNull String str, boolean allowSpaces) {
    while (allowSpaces && pos < max && Character.isWhitespace(str.charAt(pos))) ++pos;
    return pos;
  }

  private static int skipSpacesBackward(int pos, int min, @NotNull String str, boolean allowSpaces) {
    while (allowSpaces && pos > min && Character.isWhitespace(str.charAt(pos - 1))) --pos;
    return pos;
  }

  @NotNull
  protected JavaClassReference createReference(int referenceIndex,
                                               @NotNull String referenceText, 
                                               @NotNull TextRange textRange,
                                               boolean staticImport) {
    return new JavaClassReference(this, textRange, referenceIndex, referenceText, staticImport);
  }

  public boolean isAllowDollarInNames() {
    Boolean aBoolean = myProvider.getOption(JavaClassReferenceProvider.ALLOW_DOLLAR_NAMES);
    return !Boolean.FALSE.equals(aBoolean) && myElement.getLanguage() instanceof XMLLanguage;
  }

  public boolean isAllowSpaces() {
    return true;
  }

  protected boolean isStaticSeparator(char c, boolean strict) {
    return isAllowDollarInNames() ? c == DOLLAR : c == DOT;
  }

  public void reparse(@NotNull PsiElement element, @NotNull TextRange range) {
    String text = range.substring(element.getText());
    reparse(text, element, false, myContext);
  }

  public JavaClassReference getReference(int index) {
    return myReferences[index];
  }

  @NotNull
  public JavaClassReference[] getAllReferences() {
    JavaClassReference[] result = myReferences;
    if (myNestedGenericParameterReferences != null) {
      for(JavaClassReferenceSet set:myNestedGenericParameterReferences) {
        result = ArrayUtil.mergeArrays(result, set.getAllReferences());
      }
    }
    return result;
  }

  public boolean canReferencePackage(int index) {
    if (index == myReferences.length - 1) return false;
    String text = getElement().getText();
    return text.charAt(myReferences[index].getRangeInElement().getEndOffset()) != DOLLAR;
  }

  public boolean isSoft() {
    return myProvider.isSoft();
  }

  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return myReferences;
  }

  @Nullable
  public Map<CustomizableReferenceProvider.CustomizationKey, Object> getOptions() {
    return myProvider.getOptions();
  }

  @SuppressWarnings({"UnresolvedPropertyKey"})
  @NotNull
  public String getUnresolvedMessagePattern(int index){
    if (canReferencePackage(index)) {
      return JavaErrorMessages.message("error.cannot.resolve.class.or.package");
    }
    return JavaErrorMessages.message("error.cannot.resolve.class");
  }
}
