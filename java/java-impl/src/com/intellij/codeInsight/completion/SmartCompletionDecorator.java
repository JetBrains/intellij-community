/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
* @author peter
*/
public class SmartCompletionDecorator extends TailTypeDecorator<LookupElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.SmartCompletionDecorator");
  @NotNull private final Collection<ExpectedTypeInfo> myExpectedTypeInfos;
  private PsiElement myPosition;

  public SmartCompletionDecorator(LookupElement item, Collection<ExpectedTypeInfo> expectedTypeInfos) {
    super(item);
    myExpectedTypeInfos = expectedTypeInfos;
  }

  protected TailType computeTailType(InsertionContext context) {
    final TailType defType = LookupItem.getDefaultTailType(context.getCompletionChar());
    if (defType != null) {
      context.setAddCompletionChar(false);
      return defType;
    }

    final PsiExpression enclosing = PsiTreeUtil.getContextOfType(myPosition, PsiExpression.class, true);
    LookupElement item = getDelegate();

    if (enclosing != null && item.getObject() instanceof PsiElement) {
      final PsiType type = getItemType(item);
      final TailType itemType = item instanceof LookupItem ? ((LookupItem)item).getTailType() : TailType.NONE;
      TailType cached = itemType;
      int cachedPrior = 0;
      if (type != null && type.isValid()) {
        for (ExpectedTypeInfo info : myExpectedTypeInfos) {
          final PsiType infoType = info.getType();
          if (PsiType.VOID.equals(infoType)) {
            cached = info.getTailType();
            continue;
          }

          if (infoType.equals(type) && cachedPrior < 2) {
            cachedPrior = 2;
            cached = info.getTailType();
          }
          else if (cachedPrior == 2 && cached != info.getTailType()) {
            cachedPrior = 3;
            cached = itemType;
          }
          else if (((infoType.isAssignableFrom(type) && info.getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE)
                    || (type.isAssignableFrom(infoType) && info.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE))
                   && cachedPrior < 1) {
            cachedPrior = 1;
            cached = info.getTailType();
          }
          else if (cachedPrior == 1 && cached != info.getTailType()) {
            cached = itemType;
          }
        }
      }
      else {
        if (myExpectedTypeInfos.size() == 1) {
          cached = myExpectedTypeInfos.iterator().next().getTailType();
        }
      }
      return cached;
    }
    return null;
  }

  @Nullable
  private PsiType getItemType(LookupElement element) {
    return JavaCompletionUtil.getLookupElementType(element);
  }

  @Override
  public void handleInsert(InsertionContext context) {
    myPosition = getPosition(context, this);
    if (getDelegate() instanceof LookupItem) {
      analyzeItem(context, (LookupItem)getDelegate(), getObject(), myPosition, myExpectedTypeInfos);
    }
    super.handleInsert(context);
  }

  private static void analyzeItem(final InsertionContext context, final LookupItem item, final Object completion, @Nullable PsiElement position, @NotNull Collection<ExpectedTypeInfo> expectedTypes) {
    if (position == null) return;

    final PsiFile file = position.getContainingFile();

    final int startOffset = position.getTextRange().getStartOffset();
    PsiReference ref = position.getContainingFile().findReferenceAt(startOffset);

    if (ref!=null && completion instanceof PsiNamedElement) {
      if (completion instanceof PsiField) {
        final PsiMember member = (PsiMember)completion;
        if (item.getAttribute(LookupItem.FORCE_QUALIFY) != null
            && member.hasModifierProperty(PsiModifier.STATIC)
            && PsiUtil.isAccessible(member, position, null)) {
          final PsiClass containingClass = member.getContainingClass();
          if (containingClass != null) {
            final String refText = ref.getElement().getText();
            final Document document = context.getEditor().getDocument();
            document.insertString(context.getEditor().getCaretModel().getOffset(), " ");
            final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(context.getProject());
            psiDocumentManager.commitDocument(document);
            LOG.assertTrue(!psiDocumentManager.isUncommited(psiDocumentManager.getDocument(file)));
            final PsiReference finalRef = file.findReferenceAt(startOffset);
            if (finalRef == null) {
              final String text = document.getText();
              LOG.error("startOffset=" + startOffset + "\n" +
                        "caretOffset=" + context.getEditor().getCaretModel().getOffset() + "\n" +
                        "ref.getText()=" + refText + "\n" +
                        "file=" + file + "\n" +
                        "documentPart=" + text.substring(Math.max(startOffset - 100, 0), Math.min(startOffset + 100, text.length())));
            }
            final String name = member.getName();
            assert name != null;
            final PsiElement psiElement = file.getManager().performActionWithFormatterDisabled(new Computable<PsiElement>() {
              public PsiElement compute() {
                try {
                  return finalRef.bindToElement(containingClass);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
                return null;
              }
            });
            final PsiElement element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(psiElement);
            int whereToInsert = element.getTextRange().getEndOffset();
            final String insertString = "." + name;
            document.insertString(whereToInsert, insertString);
            final int endOffset = whereToInsert + insertString.length();
            context.getEditor().getCaretModel().moveToOffset(endOffset);
            context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, endOffset);
            context.getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, whereToInsert);
            context.getOffsetMap().addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, endOffset);
            item.setLookupString(name);
            document.deleteString(endOffset, endOffset + 1);
          }
        }
      }
    }

  }


  public static boolean hasUnboundTypeParams(final PsiMethod method) {
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length == 0) return false;

    final Set<PsiTypeParameter> set = new THashSet<PsiTypeParameter>(Arrays.asList(typeParameters));
    final PsiTypeVisitor<Boolean> typeParamSearcher = new PsiTypeVisitor<Boolean>() {
      public Boolean visitType(final PsiType type) {
        return true;
      }

      public Boolean visitArrayType(final PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      public Boolean visitClassType(final PsiClassType classType) {
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiTypeParameter && set.contains(aClass)) return false;

        final PsiType[] types = classType.getParameters();
        for (final PsiType psiType : types) {
          if (!psiType.accept(this).booleanValue()) return false;
        }
        return true;
      }

      public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound == null || bound.accept(this).booleanValue();
      }
    };

    for (final PsiParameter parameter : method.getParameterList().getParameters()) {
      if (!parameter.getType().accept(typeParamSearcher).booleanValue()) return false;
    }

    return true;
  }

  public static PsiSubstitutor calculateMethodReturnTypeSubstitutor(PsiMethod method, final PsiType expected) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      PsiType substitution = helper.getSubstitutionForTypeParameter(typeParameter, method.getReturnType(), expected,
                                                                    false, PsiUtil.getLanguageLevel(method));
      if (PsiType.NULL.equals(substitution)) {
        substitution = TypeConversionUtil.typeParameterErasure(typeParameter);
      }

      substitutor = substitutor.put(typeParameter, substitution);
    }
    return substitutor;
  }

  @Nullable
  public static PsiElement getPosition(InsertionContext context, LookupElement element) {
    PsiElement position = context.getFile().findElementAt(context.getStartOffset() + element.getLookupString().length() - 1);
    if (position instanceof PsiJavaToken && ">".equals(position.getText())) {
      // In case of generics class
      return position.getParent().getParent();
    }
    return position;
  }
}
