// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.numeric;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractNumberConversionIntention implements IntentionAction {
  private static final String TITLE = "Convert number to...";
  private String myText;

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return myText == null ? TITLE : myText;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert number";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    List<NumberConverter> converters = getConverters(file);
    if (converters.isEmpty()) return false;
    NumberConversionContext context = getContext(file, editor);
    if (context == null) return false;
    Number number = context.myNumber;
    String text = context.myText;
    NumberConverter singleConverter = null;
    for (NumberConverter converter : converters) {
      String convertedText = converter.getConvertedText(text, number);
      if (convertedText != null) {
        if (singleConverter != null) {
          myText = null;
          return true;
        }
        singleConverter = converter;
      }
    }
    if (singleConverter == null) return false;
    String convertedText = singleConverter.getConvertedText(text, number);
    myText = getActionName(singleConverter, convertedText);
    return true;
  }

  @Nullable
  private NumberConversionContext getContext(@NotNull PsiFile file, @NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    NumberConversionContext context = element == null ? null : extract(element);
    if (context == null && offset > 0) {
      element = file.findElementAt(offset - 1);
      context = element == null ? null : extract(element);
    }
    return context;
  }

  public String getActionName(NumberConverter converter, String convertedText) {
    return String.format("Convert number to %s (%s)", converter, convertedText);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    List<NumberConverter> converters = getConverters(file);
    if (converters.isEmpty()) return;
    NumberConversionContext context = getContext(file, editor);
    if (context == null) return;
    Number number = context.myNumber;
    String text = context.myText;
    class Conversion {
      final NumberConverter myConverter;
      final String myResult;

      Conversion(NumberConverter converter, String result) {
        myConverter = converter;
        myResult = result;
      }

      void convert() {
        WriteCommandAction.runWriteCommandAction(project, getName(), null, () -> {
          PsiElement element = context.getElement();
          if (element != null) {
            replace(element, myResult);
          }
        }, file);
      }

      private String getName() {
        return getActionName(myConverter, myResult);
      }

      @Override
      public String toString() {
        return StringUtil.capitalize(myConverter.toString()) + " (" + myResult + ")";
      }
    }
    List<Conversion> list = getConverters(file).stream()
      .map(converter -> new Conversion(converter, converter.getConvertedText(text, number)))
      .filter(conversion -> conversion.myResult != null)
      .collect(Collectors.toList());
    if (myText != null) {
      list.stream().filter(c -> c.getName().equals(myText)).findFirst().ifPresent(Conversion::convert);
      // For some reason preselected conversion is not available anymore: do nothing
      return;
    }
    JBPopup popup = JBPopupFactory.getInstance().createPopupChooserBuilder(list)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setAccessibleName(TITLE)
      .setTitle(StringUtil.wordsToBeginFromUpperCase(TITLE))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback(Conversion::convert)
      .createPopup();
    popup.showInBestPositionFor(editor);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  /**
   * Extract conversion context from given PsiElement
   * @param element an element to extract the context from
   * @return extracted context or null if given element is not a number which could be converted.
   */
  @Nullable
  @Contract(pure = true)
  protected abstract NumberConversionContext extract(@NotNull PsiElement element);

  /**
   * Returns list of converters which are applicable to given file
   * 
   * @param file file to find relevant converters
   * @return list of converters for given PsiFile
   */
  @NotNull
  @Contract(pure = true)
  protected abstract List<NumberConverter> getConverters(@NotNull PsiFile file);

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
    @NotNull final SmartPsiElementPointer<PsiElement> myElement;
    /**
     * A value of that number
     */
    @NotNull final Number myNumber;
    /**
     * A textual representation of the number
     */
    @NotNull final String myText;
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
    
    PsiElement getElement() {
      return myElement.getElement();
    }
  }
}
