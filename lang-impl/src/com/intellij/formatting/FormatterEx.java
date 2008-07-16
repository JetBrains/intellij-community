package com.intellij.formatting;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public abstract class FormatterEx{

  private static FormatterEx myTestInstance;

  public static FormatterEx getInstance() {
    final Application application = ApplicationManager.getApplication();
    if (application != null) {
      return application.getComponent(FormatterEx.class);
    }
    else {
      return getTestInstance();
    }
  }


  private static FormatterEx getTestInstance() {
    if (myTestInstance == null) {
      try {
        myTestInstance = (FormatterEx)Class.forName("com.intellij.formatting.FormatterImpl").newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return myTestInstance;
  }

  public abstract void format(FormattingModel model,
                              CodeStyleSettings settings,
                              CodeStyleSettings.IndentOptions indentOptions,
                              TextRange affectedRange,
                              final boolean processHeadingWhitespace) throws IncorrectOperationException;

  public abstract void format(FormattingModel model,
                              CodeStyleSettings settings,
                              CodeStyleSettings.IndentOptions indentOptions,
                              CodeStyleSettings.IndentOptions javaIndentOptions,
                              TextRange affectedRange,
                              final boolean processHeadingWhitespace) throws IncorrectOperationException;


  public abstract IndentInfo getWhiteSpaceBefore(final FormattingDocumentModel psiBasedFormattingModel,
                                                 final Block block,
                                                 final CodeStyleSettings settings,
                                                 final CodeStyleSettings.IndentOptions indentOptions,
                                                 final TextRange textRange, final boolean mayChangeLineFeeds);

  public abstract int adjustLineIndent(final FormattingModel psiBasedFormattingModel,
                                       final CodeStyleSettings settings,
                                       final CodeStyleSettings.IndentOptions indentOptions,
                                       final int offset,
                                       final TextRange affectedRange) throws IncorrectOperationException;

  @Nullable
  public abstract String getLineIndent(final FormattingModel psiBasedFormattingModel,
                                       final CodeStyleSettings settings,
                                       final CodeStyleSettings.IndentOptions indentOptions,
                                       final int offset,
                                       final TextRange affectedRange);

  public abstract void adjustTextRange(final FormattingModel documentModel,
                                       final CodeStyleSettings settings,
                                       final CodeStyleSettings.IndentOptions indentOptions,
                                       final TextRange textRange,
                                       final boolean keepBlankLines,
                                       final boolean keepLineBreaks,
                                       final boolean changeWSBeforeFirstElement,
                                       final boolean changeLineFeedsBeforeFirstElement, final IndentInfoStorage indentInfoStorage);

  public abstract void saveIndents(final FormattingModel model, final TextRange affectedRange,
                                   IndentInfoStorage storage,
                                   final CodeStyleSettings settings,
                                   final CodeStyleSettings.IndentOptions indentOptions);

  public abstract boolean isDisabled();



  public abstract void adjustLineIndentsForRange(final FormattingModel model,
                                                 final CodeStyleSettings settings,
                                                 final CodeStyleSettings.IndentOptions indentOptions,
                                                 final TextRange rangeToAdjust);

  public abstract void formatAroundRange(final FormattingModel model, final CodeStyleSettings settings,
                                         final TextRange textRange, final FileType fileType);

  public abstract void adjustTextRange(FormattingModel model,
                                       CodeStyleSettings settings,
                                       CodeStyleSettings.IndentOptions indentOptions,
                                       TextRange affectedRange);

  public interface IndentInfoStorage {
    void saveIndentInfo(IndentInfo info, int startOffset);

    IndentInfo getIndentInfo(int startOffset);
  }

  public static FormatterEx getInstanceEx() {
    return getInstance();
  }

}
