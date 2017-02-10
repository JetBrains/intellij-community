/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.surroundWith;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SimpleSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {
    new GroupSurrounder("Capturing Group (pattern)", "("),
    new GroupSurrounder("Non-Capturing Group (?:pattern)", "(?:"),
  };

  @NotNull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    // adjust start/end
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
    }

    RegExpElement pattern = findElementAtStrict(file, startOffset, endOffset, RegExpPattern.class);
    if (pattern != null) return new RegExpElement[]{pattern};

    RegExpElement branch = findElementAtStrict(file, startOffset, endOffset, RegExpBranch.class);
    if (branch != null) return new RegExpElement[]{branch};

    List<PsiElement> atoms = new ArrayList<>();
    RegExpAtom atom = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, RegExpAtom.class);
    for (; atom != null; atom = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, RegExpAtom.class)) {
      atoms.add(atom);
      startOffset = atom.getTextRange().getEndOffset();

      // handle embedded whitespace
      if ((element1 = file.findElementAt(startOffset)) instanceof PsiWhiteSpace) {
        startOffset = element1.getTextRange().getEndOffset();
        atoms.add(element1);
      }
    }

    if (startOffset == endOffset && atoms.size() > 0) {
      PsiElement[] elements = PsiUtilCore.toPsiElementArray(atoms);
      if ((atoms.size() == 1 || PsiTreeUtil.findCommonParent(elements) == elements[0].getParent())) {
        return elements;
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  @Nullable
  private static <T extends RegExpElement> T findElementAtStrict(PsiFile file, int startOffset, int endOffset, Class<T> clazz) {
    T element = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, clazz);
    if (element == null || element.getTextRange().getEndOffset() < endOffset) return null;
    return element;
  }
}
