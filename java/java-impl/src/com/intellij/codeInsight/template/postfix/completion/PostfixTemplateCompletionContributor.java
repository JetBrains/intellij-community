package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.string;

public class PostfixTemplateCompletionContributor extends CompletionContributor {
  private static final TokenSet SUITABLE_ELEMENTS = TokenSet.orSet(ElementType.KEYWORD_BIT_SET,
                                                                   ElementType.LITERAL_BIT_SET,
                                                                   TokenSet.create(JavaTokenType.IDENTIFIER));

  public PostfixTemplateCompletionContributor() {
    extend(CompletionType.BASIC, identifierAfterDot(), new PostfixTemplatesCompletionProvider());
  }

  @Nullable
  public static PostfixLiveTemplate getPostfixLiveTemplate(@NotNull PsiFile file, int offset) {
    PostfixLiveTemplate postfixLiveTemplate = CustomLiveTemplate.EP_NAME.findExtension(PostfixLiveTemplate.class);
    return postfixLiveTemplate != null && postfixLiveTemplate.isApplicable(file, offset, false) ? postfixLiveTemplate : null;
  }

  private static ElementPattern<? extends PsiElement> identifierAfterDot() {
    return psiElement().withElementType(SUITABLE_ELEMENTS).afterLeaf(psiElement().withText(string().endsWith(".")));
  }
}
