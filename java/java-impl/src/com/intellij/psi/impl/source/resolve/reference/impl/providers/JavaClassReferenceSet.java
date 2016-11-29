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
  private static final char LT = '<';
  private static final char COMMA = ',';

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
    final List<JavaClassReference> referencesList = new ArrayList<>();
    int currentDot = -1;
    int referenceIndex = 0;
    boolean allowDollarInNames = isAllowDollarInNames();
    boolean allowGenerics = false;
    boolean allowGenericsCalculated = false;
    boolean parsingClassNames = true;

    while (parsingClassNames) {
      int nextDotOrDollar = -1;
      for(int curIndex = currentDot + 1; curIndex < str.length(); ++curIndex) {
        final char ch = str.charAt(curIndex);

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
        for(int i = nextDotOrDollar; i < str.length() && Character.isJavaIdentifierPart(str.charAt(i)); ++i) nextDotOrDollar++;
        parsingClassNames = false;
        int j = nextDotOrDollar;
        while(j < str.length() && Character.isWhitespace(str.charAt(j))) ++j;

        if (j < str.length()) {
          char ch = str.charAt(j);
          boolean recognized = false;

          if (ch == '[') {
            j++;
            while(j < str.length() && Character.isWhitespace(str.charAt(j))) ++j;

            if (j < str.length()) {
              ch = str.charAt(j);

              if (ch == ']') {
                j++;
                while(j < str.length() && Character.isWhitespace(str.charAt(j))) ++j;

                recognized = j == str.length();
              }
            }
          }

          final Boolean aBoolean = JavaClassReferenceProvider.JVM_FORMAT.getValue(getOptions());
          if (aBoolean == null || !aBoolean.booleanValue()) {
            if (!recognized) nextDotOrDollar = -1; // nonsensible characters anyway, don't do resolve
          }
        }
      }

      if (nextDotOrDollar != -1 && nextDotOrDollar < str.length()) {
        final char c = str.charAt(nextDotOrDollar);
        if (c == LT) {
          int end = str.lastIndexOf('>');
          if (end != -1 && end > nextDotOrDollar) {
            if (myNestedGenericParameterReferences == null) myNestedGenericParameterReferences = new ArrayList<>(1);
            myNestedGenericParameterReferences.add(
              new JavaClassReferenceSet(
                str.substring(nextDotOrDollar + 1, end),
                myElement,
                myStartInElement + nextDotOrDollar + 1,
                isStaticImport,
                myProvider,
                this
              )
            );
            parsingClassNames = false;
          } else {
            nextDotOrDollar = -1; // nonsensible characters anyway, don't do resolve
          }
        } else if (COMMA == c && myContext != null) {
          if (myContext.myNestedGenericParameterReferences == null) myContext.myNestedGenericParameterReferences = new ArrayList<>(1);
          myContext.myNestedGenericParameterReferences.add(
            new JavaClassReferenceSet(
              str.substring(nextDotOrDollar + 1),
              myElement,
              myStartInElement + nextDotOrDollar + 1,
              isStaticImport,
              myProvider,
              this
            )
          );
          parsingClassNames = false;
        }
      }

      int beginIndex = currentDot + 1;
      while (beginIndex < nextDotOrDollar && Character.isWhitespace(str.charAt(beginIndex))) beginIndex++;

      final String subreferenceText = nextDotOrDollar > 0 ? str.substring(beginIndex, nextDotOrDollar) : str.substring(beginIndex);

      TextRange textRange =
        new TextRange(myStartInElement + beginIndex, myStartInElement + (nextDotOrDollar > 0 ? nextDotOrDollar : str.length()));
      JavaClassReference currentContextRef = createReference(referenceIndex, subreferenceText, textRange, isStaticImport);
      referenceIndex++;
      referencesList.add(currentContextRef);
      if ((currentDot = nextDotOrDollar) < 0) {
        break;
      } 
    }

    myReferences = referencesList.toArray(new JavaClassReference[referencesList.size()]);
  }

  @NotNull
  protected JavaClassReference createReference(final int referenceIndex, @NotNull String subreferenceText, @NotNull TextRange textRange,
                                               final boolean staticImport) {
    return new JavaClassReference(this, textRange, referenceIndex, subreferenceText, staticImport);
  }

  public boolean isAllowDollarInNames() {
    final Boolean aBoolean = myProvider.getOption(JavaClassReferenceProvider.ALLOW_DOLLAR_NAMES);
    return !Boolean.FALSE.equals(aBoolean) && myElement.getLanguage() instanceof XMLLanguage;
  }

  protected boolean isStaticSeparator(char c, boolean strict) {
    return isAllowDollarInNames() ? c == DOLLAR : c == DOT;
  }

  public void reparse(@NotNull PsiElement element, @NotNull TextRange range) {
    final String text = range.substring(element.getText());
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
    return text.charAt(myReferences[index].getRangeInElement().getEndOffset()) != '$';
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
