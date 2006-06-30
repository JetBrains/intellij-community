package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding;
import com.intellij.util.ArrayUtil;
import org.apache.commons.collections.set.ListOrderedSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: May 11, 2006
 * Time: 3:48:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class FastWordCompletionHandler implements CodeInsightActionHandler {
  private FastWordCompletionAction myFastWordCompletionAction;

  public FastWordCompletionHandler(final FastWordCompletionAction fastWordCompletionAction) {
    myFastWordCompletionAction = fastWordCompletionAction;
  }

  public void invoke(Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    final CharSequence charsSequence = editor.getDocument().getCharsSequence();

    final Pair<String, Integer> p = getPrefix(editor, charsSequence);
    String currentPrefix = p.first;
    int startOffset = p.second.intValue();
    String oldPrefix = myFastWordCompletionAction.getOldPrefix();

    if (oldPrefix == null || !currentPrefix.startsWith(oldPrefix) || oldPrefix.length() == 0 ||
      !currentPrefix.equals(myFastWordCompletionAction.getLastProposedVariant())) {
      oldPrefix = currentPrefix;
      myFastWordCompletionAction.setOldPrefix(oldPrefix);
    }

    Set variants = computeVariants(editor);

    for (Iterator i = variants.iterator(); i.hasNext();) {
      String s = (String)i.next();

      if (!s.startsWith(oldPrefix)) i.remove();
    }


    String nextVariant = (String)variants.iterator().next();

    for (Iterator i = variants.iterator(); i.hasNext();) {
      String s = (String)i.next();

      if (s.equals(currentPrefix)) {
        if (i.hasNext()) {
          nextVariant = (String)i.next();
          break;
        }
      }
    }

    editor.getDocument().replaceString(startOffset,  startOffset + currentPrefix.length(), nextVariant);
    editor.getCaretModel().moveToOffset(startOffset + nextVariant.length());
    myFastWordCompletionAction.setLastProposedVariant(nextVariant);
  }

  private static ListOrderedSet computeVariants(final Editor editor) {
    final char[] chars = editor.getDocument().getText().toCharArray();

    final ArrayList<String> words = new ArrayList<String>();
    final List<String> afterWords = new ArrayList<String>();

    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor(){
      public void run(final char[] chars, final int start, final int end) {
        final int caretOffset = editor.getCaretModel().getOffset();
        if (start <= caretOffset && end >= caretOffset) return; //skip prefix itself

        final String word = new String(chars, start, end - start);
        if (end > caretOffset) afterWords.add(word);
        else words.add(word);
      }
    }, chars, 0, chars.length);

    ListOrderedSet variants = new ListOrderedSet();

    for (int i = words.size() - 1; i >= 0 ; i--) {
      String s = words.get(i);
      variants.add(s);
    }

    variants.addAll(afterWords);
    return variants;
  }

  private static Pair<String, Integer> getPrefix(final Editor editor, final CharSequence charsSequence) {
    int offset = editor.getCaretModel().getOffset();

    while (offset > 1 && Character.isJavaIdentifierPart(charsSequence.charAt(offset - 1))) offset--;

    int endOffset = offset;
    while (Character.isJavaIdentifierPart(charsSequence.charAt(endOffset)) &&
      endOffset < editor.getDocument().getTextLength()) endOffset++;

    String prefix = charsSequence.subSequence(offset, endOffset).toString();
    return new Pair<String, Integer>(prefix, offset);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static Object[] getAllWords(final PsiElement context, final String prefix, final int offset) {
    if (prefix.length() == 0) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final char [] chars = context.getContainingFile().getText().toCharArray();
    final List<String> objs = new ArrayList<String>();
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor(){
      public void run(final char[] chars, final int start, final int end) {
        final int len = end - start;
        if (start > offset || offset >= end) {
          objs.add(String.valueOf(chars, start, len));
        }
      }
    }, chars, 0, chars.length);
    return objs.toArray();
  }
}
