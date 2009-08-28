package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Mike
 */
public class SelectWordUtil {
    
  private static ExtendWordSelectionHandler[] SELECTIONERS = new ExtendWordSelectionHandler[]{
  };

  private static boolean ourExtensionsLoaded = false;

  public static void registerSelectioner(ExtendWordSelectionHandler selectioner) {
    SELECTIONERS = ArrayUtil.append(SELECTIONERS, selectioner);
  }

  static ExtendWordSelectionHandler[] getExtendWordSelectionHandlers() {
    if (!ourExtensionsLoaded) {
      ourExtensionsLoaded = true;
      for (ExtendWordSelectionHandler handler : Extensions.getExtensions(ExtendWordSelectionHandler.EP_NAME)) {
        registerSelectioner(handler);        
      }
    }
    return SELECTIONERS;
  }

  public static void addWordSelection(boolean camel, CharSequence editorText, int cursorOffset, @NotNull List<TextRange> ranges) {
    TextRange camelRange = camel ? getCamelSelectionRange(editorText, cursorOffset) : null;
    if (camelRange != null) {
      ranges.add(camelRange);
    }

    TextRange range = getWordSelectionRange(editorText, cursorOffset);
    if (range != null && !range.equals(camelRange)) {
      ranges.add(range);
    }
  }

  private static TextRange getCamelSelectionRange(CharSequence editorText, int cursorOffset) {
    if (cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset + 1;
      final int textLen = editorText.length();

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        final char prevChar = editorText.charAt(start - 1);
        final char curChar = editorText.charAt(start);
        final char nextChar = start + 1 < textLen ? editorText.charAt(start + 1) : 0; // 0x00 is not lowercase.

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar == '_' && curChar != '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        start--;
      }

      while (end < textLen && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        final char prevChar = editorText.charAt(end - 1);
        final char curChar = editorText.charAt(end);
        final char nextChar = end + 1 < textLen ? editorText.charAt(end + 1) : 0; // 0x00 is not lowercase

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar != '_' && curChar == '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        end++;
      }

      if (start + 1 < end) {
        return new TextRange(start, end);
      }
    }

    return null;
  }

  public static TextRange getWordSelectionRange(CharSequence editorText, int cursorOffset) {
    if (editorText.length() == 0) return null;
    if (cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset;

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        start--;
      }

      while (end < editorText.length() && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        end++;
      }

      return new TextRange(start, end);
    }

    return null;
  }

}
