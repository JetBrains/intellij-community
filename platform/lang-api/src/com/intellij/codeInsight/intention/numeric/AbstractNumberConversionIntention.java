// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.numeric;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.LangBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractNumberConversionIntention implements ModCommandAction {

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return CodeInsightBundle.message("intention.family.convert.number");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext actionCtx) {
    PsiFile file = actionCtx.file();
    if (!BaseIntentionAction.canModify(file)) return null;
    List<NumberConverter> converters = getConverters(file);
    if (converters.isEmpty()) return null;
    NumberConversionContext context = getContext(actionCtx);
    if (context == null) return null;
    Number number = context.myNumber;
    String text = context.myText;
    NumberConverter singleConverter = null;
    for (NumberConverter converter : converters) {
      String convertedText = converter.getConvertedText(text, number);
      if (convertedText != null) {
        if (singleConverter != null) {
          return Presentation.of(LangBundle.message("intention.name.convert.number.to"));
        }
        singleConverter = converter;
      }
    }
    if (singleConverter == null) return null;
    String convertedText = singleConverter.getConvertedText(text, number);
    return Presentation.of(LangBundle.message("intention.name.convert.number.to.with.text", singleConverter, convertedText));
  }

  private @Nullable NumberConversionContext getContext(@NotNull ActionContext actionContext) {
    PsiElement element = actionContext.findLeaf();
    NumberConversionContext context = element == null ? null : extract(element);
    if (context == null) {
      element = actionContext.findLeafOnTheLeft();
      context = element == null ? null : extract(element);
    }
    return context;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext actionContext) {
    List<NumberConverter> converters = getConverters(actionContext.file());
    if (converters.isEmpty()) return ModCommand.nop();
    NumberConversionContext context = getContext(actionContext);
    if (context == null) return ModCommand.nop();
    Number number = context.myNumber;
    String text = context.myText;
    class Conversion implements ModCommandAction {
      final NumberConverter myConverter;
      final String myResult;

      Conversion(NumberConverter converter, String result) {
        myConverter = converter;
        myResult = result;
      }

      @Override
      public @NotNull ModCommand perform(@NotNull ActionContext ctx) {
        PsiElement element = context.getElement();
        if (element == null) return ModCommand.nop();
        return ModCommand.psiUpdate(element, e -> replace(e, myResult));
      }

      @Override
      public @Nullable Presentation getPresentation(@NotNull ActionContext ctx) {
        if (context.getElement() == null) return null;
        return Presentation.of(toString());
      }

      @Override
      public @NlsSafe String toString() {
        return StringUtil.capitalize(myConverter.toString()) + " (" + myResult + ")";
      }

      @Override
      public @NotNull String getFamilyName() {
        return myConverter.toString();
      }
    }
    List<Conversion> list = getConverters(actionContext.file()).stream()
      .map(converter -> new Conversion(converter, converter.getConvertedText(text, number)))
      .filter(conversion -> conversion.myResult != null)
      .toList();
    return ModCommand.chooseAction(LangBundle.message("intention.name.convert.number.to.title"), list);
  }

  /**
   * Extract conversion context from given PsiElement
   * @param element an element to extract the context from
   * @return extracted context or null if given element is not a number which could be converted.
   */
  @Contract(pure = true)
  protected abstract @Nullable NumberConversionContext extract(@NotNull PsiElement element);

  /**
   * Returns list of converters which are applicable to given file
   * 
   * @param file file to find relevant converters
   * @return list of converters for given PsiFile
   */
  @Contract(pure = true)
  protected abstract @NotNull List<NumberConverter> getConverters(@NotNull PsiFile file);

  /**
   * Performs a replacement of given source number with the conversion result.
   * 
   * @param sourceElement element to replace (previously returned by extract in {@link NumberConversionContext}).
   * @param replacement replacement text
   */
  protected abstract void replace(PsiElement sourceElement, String replacement);

  /**
   * A context for number conversion
   */
  protected static class NumberConversionContext {
    /**
     * An element which represents a number to be converted
     */
    final @NotNull SmartPsiElementPointer<PsiElement> myElement;
    /**
     * A value of that number
     */
    final @NotNull Number myNumber;
    /**
     * A textual representation of the number
     */
    final @NotNull String myText;
    /**
     * Whether there's a separate negation (unary minus) applied to the number. If true, {@link #myText} doesn't include that negation,
     * but {@link #myNumber} is properly negated and {@link #myElement} points to the unary minus expression.
     */
    final boolean myNegated;

    public NumberConversionContext(@NotNull PsiElement element, @NotNull Number number, @NotNull String text, boolean negated) {
      myElement = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
      myNumber = number;
      myText = text;
      myNegated = negated;
    }

    @Nullable
    PsiElement getElement() {
      return myElement.getElement();
    }

    public @NotNull Project getProject() {
      return myElement.getProject();
    }
  }
}
