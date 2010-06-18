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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PlatformPatterns.character;

public class CompletionUtil {
  public static final Key<TailType> TAIL_TYPE_ATTR = LookupItem.TAIL_TYPE_ATTR;

  private static final CompletionData ourGenericCompletionData = new CompletionData() {
    {
      final CompletionVariant variant = new CompletionVariant(PsiElement.class, TrueFilter.INSTANCE);
      variant.addCompletionFilter(TrueFilter.INSTANCE, TailType.NONE);
      registerVariant(variant);
    }
  };
  private static final HashMap<FileType, NotNullLazyValue<CompletionData>> ourCustomCompletionDatas = new HashMap<FileType, NotNullLazyValue<CompletionData>>();

  public static final @NonNls String DUMMY_IDENTIFIER = CompletionInitializationContext.DUMMY_IDENTIFIER;
  public static final @NonNls String DUMMY_IDENTIFIER_TRIMMED = DUMMY_IDENTIFIER.trim();

  public static boolean startsWith(String text, String prefix) {
    //if (text.length() <= prefix.length()) return false;
    return toLowerCase(text).startsWith(toLowerCase(prefix));
  }

  private static String toLowerCase(String text) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    switch (settings.COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return text.toLowerCase();

      case CodeInsightSettings.FIRST_LETTER: {
        StringBuffer buffer = new StringBuffer();
        buffer.append(text.toLowerCase());
        if (buffer.length() > 0) {
          buffer.setCharAt(0, text.charAt(0));
        }
        return buffer.toString();
      }

      default:
        return text;
    }
  }

  public static CompletionData getCompletionDataByElement(final PsiElement position, PsiFile originalFile) {
    final FileType fileType = position.getParent().getLanguage().getAssociatedFileType();
    if (fileType != null) {
      final CompletionData mainData = getCompletionDataByFileType(fileType);
      if (mainData != null) {
        return mainData;
      }
    }

    final CompletionData mainData = getCompletionDataByFileType(originalFile.getFileType());
    return mainData != null ? mainData : ourGenericCompletionData;
  }

  public static void registerCompletionData(FileType fileType, NotNullLazyValue<CompletionData> completionData) {
    ourCustomCompletionDatas.put(fileType, completionData);
  }
  
  public static void registerCompletionData(FileType fileType, final CompletionData completionData) {
    registerCompletionData(fileType, new NotNullLazyValue<CompletionData>() {
      @NotNull
      protected CompletionData compute() {
        return completionData;
      }
    });
  }

  @Nullable
  public static CompletionData getCompletionDataByFileType(FileType fileType) {
    for(CompletionDataEP ep: Extensions.getExtensions(CompletionDataEP.EP_NAME)) {
      if (ep.fileType.equals(fileType.getName())) {
        return ep.getHandler();
      }
    }
    final NotNullLazyValue<CompletionData> lazyValue = ourCustomCompletionDatas.get(fileType);
    return lazyValue == null ? null : lazyValue.getValue();
  }


  static boolean isOverwrite(final LookupElement item, final char completionChar) {
    return completionChar != Lookup.AUTO_INSERT_SELECT_CHAR
      ? completionChar == Lookup.REPLACE_SELECT_CHAR
      : item.getUserData(LookupItem.OVERWRITE_ON_AUTOCOMPLETE_ATTR) != null;
  }


  public static boolean shouldShowFeature(final CompletionParameters parameters, @NonNls final String id) {
    return FeatureUsageTracker.getInstance().isToBeShown(id, parameters.getPosition().getProject());
  }

  public static String findJavaIdentifierPrefix(final PsiElement insertedElement, final int offset) {
    return findIdentifierPrefix(insertedElement, offset, character().javaIdentifierPart(), character().javaIdentifierStart());
  }

  public static String findIdentifierPrefix(PsiElement insertedElement, int offset, ElementPattern<Character> idPart,
                                             ElementPattern<Character> idStart) {
    if(insertedElement == null) return "";
    final String text = insertedElement.getText();
    final int offsetInElement = offset - insertedElement.getTextRange().getStartOffset();
    int start = offsetInElement - 1;
    while (start >=0 ) {
      if (!idPart.accepts(text.charAt(start))) break;
      --start;
    }
    while (start + 1 < offsetInElement && !idStart.accepts(text.charAt(start + 1))) {
      start++;
    }

    return text.substring(start + 1, offsetInElement).trim();
  }

  static InsertionContext emulateInsertion(InsertionContext oldContext, int newStart, final LookupElement item) {
    final InsertionContext newContext = newContext(oldContext, item);
    emulateInsertion(item, newStart, newContext);
    return newContext;
  }

  private static InsertionContext newContext(InsertionContext oldContext, LookupElement forElement) {
    final Editor editor = oldContext.getEditor();
    return new InsertionContext(new OffsetMap(editor.getDocument()), Lookup.AUTO_INSERT_SELECT_CHAR, new LookupElement[]{forElement}, oldContext.getFile(), editor);
  }

  public static InsertionContext newContext(InsertionContext oldContext, LookupElement forElement, int startOffset, int tailOffset) {
    final InsertionContext context = newContext(oldContext, forElement);
    setOffsets(context, startOffset, tailOffset);
    return context;
  }

  public static void emulateInsertion(LookupElement item, int offset, InsertionContext context) {
    setOffsets(context, offset, offset);

    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    final String lookupString = item.getLookupString();

    document.insertString(offset, lookupString);
    editor.getCaretModel().moveToOffset(context.getTailOffset());
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
    item.handleInsert(context);
  }

  private static void setOffsets(InsertionContext context, int offset, final int tailOffset) {
    final OffsetMap offsetMap = context.getOffsetMap();
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, offset);
    offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, tailOffset);
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, tailOffset);
    context.setTailOffset(tailOffset);
  }
}
