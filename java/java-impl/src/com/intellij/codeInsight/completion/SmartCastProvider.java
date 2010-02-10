package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.simple.RParenthTailType;
import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
class SmartCastProvider extends CompletionProvider<CompletionParameters> {
  static final PsiElementPattern.Capture<PsiElement> INSIDE_TYPECAST_TYPE = PlatformPatterns.psiElement().afterLeaf(
    PlatformPatterns.psiElement().withText("(").withParent(
      PsiTypeCastExpression.class));
  public static final Key<Boolean> TYPE_CAST = Key.create("TYPE_CAST");

  protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
    for (final ExpectedTypeInfo type : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      if (type.getType() == PsiType.VOID) {
        continue;
      }

      final boolean overwrite = INSIDE_TYPECAST_TYPE.accepts(parameters.getOriginalPosition());

      final LookupElement item = AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE.applyPolicy(new LookupElementDecorator<LookupItem>(
        PsiTypeLookupItem.createLookupItem(type.getDefaultType(), parameters.getPosition())) {

        @Override
        public void handleInsert(InsertionContext context) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.casting");

          final Editor editor = context.getEditor();
          final Document document = editor.getDocument();
          if (overwrite) {
            document.deleteString(context.getSelectionEndOffset(),
                                  context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
          }

          final CodeStyleSettings csSettings = CodeStyleSettingsManager.getSettings(context.getProject());
          final int oldTail = context.getTailOffset();
          context.setTailOffset(RParenthTailType.addRParenth(editor, oldTail, csSettings.SPACE_WITHIN_CAST_PARENTHESES));

          //todo let the delegate handle insertion itself
          final LookupItem typeItem = getDelegate();
          final InsertionContext typeContext = CompletionUtil.newContext(context, typeItem, context.getStartOffset(), oldTail);
          new DefaultInsertHandler().handleInsert(typeContext, typeItem);
          PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();

          if (csSettings.SPACE_AFTER_TYPE_CAST) {
            context.setTailOffset(TailType.insertChar(editor, context.getTailOffset(), ' '));
          }

          editor.getCaretModel().moveToOffset(context.getTailOffset());
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      });
      item.putUserData(TYPE_CAST, Boolean.TRUE);
      result.addElement(item);
    }
  }
}
