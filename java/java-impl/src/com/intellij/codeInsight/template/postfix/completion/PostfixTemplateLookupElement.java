package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.completion.PostfixTemplateCompletionContributor.getPostfixLiveTemplate;

class PostfixTemplateLookupElement extends LiveTemplateLookupElement {
  @NotNull 
  private final PostfixTemplate myTemplate;

  public PostfixTemplateLookupElement(@NotNull PostfixTemplate template, char shortcut) {
    super(createStubTemplate(template, shortcut), template.getPresentableName(), true, true);
    myTemplate = template;
  }

  @NotNull
  public PostfixTemplate getPostfixTemplate() {
    return myTemplate;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTailText(" " + arrow() + " " + myTemplate.getExample());
  }

  @Override
  public void handleInsert(InsertionContext context) {
    context.setAddCompletionChar(false);
    int lengthOfTypedKey = context.getTailOffset() - context.getStartOffset();
    String templateKey = myTemplate.getKey();
    Editor editor = context.getEditor();
    if (lengthOfTypedKey < templateKey.length()) {
      context.getDocument().insertString(context.getTailOffset(), templateKey.substring(lengthOfTypedKey));
      editor.getCaretModel().moveToOffset(context.getTailOffset() + templateKey.length() - lengthOfTypedKey);
    }

    PsiFile file = context.getFile();

    PostfixLiveTemplate postfixLiveTemplate = getPostfixLiveTemplate(file, editor.getCaretModel().getOffset());
    if (postfixLiveTemplate != null) {
      postfixLiveTemplate.expand(templateKey, new CustomTemplateCallback(editor, file, false));
    }
  }

  private static TemplateImpl createStubTemplate(@NotNull PostfixTemplate postfixTemplate, char shortcut) {
    TemplateImpl template = new TemplateImpl(postfixTemplate.getKey(), "postfixTemplate");
    template.setShortcutChar(shortcut);
    return template;
  }

  private static String arrow() {
    return SystemInfo.isMac ? "→" :"->";
  }
}
