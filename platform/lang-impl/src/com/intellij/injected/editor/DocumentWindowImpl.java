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

package com.intellij.injected.editor;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey
 */
public class DocumentWindowImpl extends UserDataHolderBase implements Disposable, DocumentWindow, DocumentEx {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.editor.impl.injected.DocumentRangee");
  private final DocumentEx myDelegate;
  private final boolean myOneLine;
  private Place myShreds;
  private final int myPrefixLineCount;
  private final int mySuffixLineCount;

  public DocumentWindowImpl(@NotNull DocumentEx delegate, boolean oneLine, Place shreds) {
    myDelegate = delegate;
    myOneLine = oneLine;
    myShreds = shreds;
    myPrefixLineCount = Math.max(1, 1 + StringUtil.countNewLines(myShreds.get(0).prefix));
    mySuffixLineCount = Math.max(1, 1 + StringUtil.countNewLines(myShreds.get(shreds.size()- 1).suffix));
  }

  public int getLineCount() {
    return 1 + StringUtil.countNewLines(getText());
  }

  public int getLineStartOffset(int line) {
    //int oldso = oldso(line);
    int newso = newso(line);
    //if (newso != oldso) {
    //  int i = 0;
    //}

    return newso;
  }

  private int newso(int line) {
    LOG.assertTrue(line >= 0, line);
    String hostText = myDelegate.getText();

    int curLine = 0;
    int startOffset = 0;
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      RangeMarker hostRange = shred.getHostRangeMarker();
      if (!hostRange.isValid()) continue;

      startOffset += shred.prefix.length();
      curLine += StringUtil.getLineBreakCount(shred.prefix);
      if (curLine >= line) return startOffset;
      String text = hostText.substring(hostRange.getStartOffset(), hostRange.getEndOffset());

      for (int i=text.indexOf('\n'), offsetInside = 0; i!=-1;i=text.substring(offsetInside).indexOf('\n')) {
        curLine++;
        offsetInside += i + 1;
        if (curLine >= line) return startOffset + offsetInside;
      }

      startOffset += text.length();

      text = shred.suffix;
      for (int i=text.indexOf('\n'), offsetInside = 0; i!=-1;i=text.substring(offsetInside).indexOf('\n')) {
        curLine++;
        offsetInside += i + 1;
        if (curLine >= line) return startOffset + offsetInside;
      }
      startOffset += text.length();
    }

    return startOffset;
  }

  private int oldso(int line) {
    assert line >= 0 : line;
    return new DocumentImpl(getText()).getLineStartOffset(line);
  }

  public int getLineEndOffset(int line) {
    LOG.assertTrue(line >= 0, line);
    int startOffsetOfNextLine = getLineStartOffset(line + 1);
    return startOffsetOfNextLine == 0 || getText().charAt(startOffsetOfNextLine - 1) != '\n' ? startOffsetOfNextLine : startOffsetOfNextLine - 1;
  }

  public String getText() {
    StringBuilder text = new StringBuilder();
    String hostText = myDelegate.getText();
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      RangeMarker hostRange = shred.getHostRangeMarker();
      if (hostRange.isValid()) {
        text.append(shred.prefix);
        text.append(hostText, hostRange.getStartOffset(), hostRange.getEndOffset());
        text.append(shred.suffix);
      }
    }
    return text.toString();
  }

  @Override
  public String getText(TextRange range) {
    return range.substring(getText());
  }

  @NotNull
  public CharSequence getCharsSequence() {
    return getText();
  }

  @NotNull
  public char[] getChars() {
    return CharArrayUtil.fromSequence(getText());
  }

  public int getTextLength() {
    int length = 0;
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      RangeMarker hostRange = shred.getHostRangeMarker();
      length += shred.prefix.length();
      length += hostRange.getEndOffset() - hostRange.getStartOffset();
      length += shred.suffix.length();
    }
    return length;
  }

  public int getLineNumber(int offset) {
    int lineNumber = 0;
    String hostText = myDelegate.getText();
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      String prefix = shred.prefix;
      String suffix = shred.suffix;
      lineNumber += StringUtil.getLineBreakCount(prefix.substring(0, Math.min(offset, prefix.length())));
      if (offset < prefix.length()) {
        return lineNumber;
      }
      offset -= prefix.length();

      RangeMarker currentRange = shred.getHostRangeMarker();
      if (!currentRange.isValid()) continue;
      int rangeLength = currentRange.getEndOffset() - currentRange.getStartOffset();
      String rangeText = hostText.substring(currentRange.getStartOffset(), currentRange.getEndOffset());

      lineNumber += StringUtil.getLineBreakCount(rangeText.substring(0, Math.min(offset, rangeLength)));
      if (offset < rangeLength) {
        return lineNumber;
      }
      offset -= rangeLength;

      lineNumber += StringUtil.getLineBreakCount(suffix.substring(0, Math.min(offset, suffix.length())));
      if (offset < suffix.length()) {
        return lineNumber;
      }

      offset -= suffix.length();
    }
    lineNumber = getLineCount() - 1;
    return lineNumber < 0 ? 0 : lineNumber;
  }

  public TextRange getHostRange(int hostOffset) {
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      RangeMarker currentRange = shred.getHostRangeMarker();
      TextRange textRange = InjectedLanguageUtil.toTextRange(currentRange);
      if (textRange.grown(1).contains(hostOffset)) return textRange;
    }                              
    return null;
  }

  public void insertString(final int offset, @NotNull CharSequence s) {
    LOG.assertTrue(offset >= myShreds.get(0).prefix.length());
    LOG.assertTrue(offset <= getTextLength() - myShreds.get(myShreds.size() - 1).suffix.length());
    if (isOneLine()) {
      s = StringUtil.replace(s.toString(), "\n", "");
    }
    myDelegate.insertString(injectedToHost(offset), s);
  }

  public void deleteString(final int startOffset, final int endOffset) {
    assert intersectWithEditable(new TextRange(startOffset, startOffset)) != null;
    assert intersectWithEditable(new TextRange(endOffset, endOffset)) != null;

    List<TextRange> hostRangesToDelete = new ArrayList<TextRange>(myShreds.size());

    int offset = startOffset;
    int curRangeStart = 0;
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      curRangeStart += shred.prefix.length();
      if (offset < curRangeStart) offset = curRangeStart;
      if (offset >= endOffset) break;
      RangeMarker hostRange = shred.getHostRangeMarker();
      if (!hostRange.isValid()) continue;
      int hostRangeLength = hostRange.getEndOffset() - hostRange.getStartOffset();
      TextRange range = TextRange.from(curRangeStart, hostRangeLength);
      if (range.contains(offset)) {
        TextRange rangeToDelete = new TextRange(offset, Math.min(range.getEndOffset(), endOffset));
        hostRangesToDelete.add(rangeToDelete.shiftRight(hostRange.getStartOffset() - curRangeStart));
        offset = rangeToDelete.getEndOffset();
      }
      curRangeStart += hostRangeLength;
      curRangeStart += shred.suffix.length();
    }

    int delta = 0;
    for (TextRange hostRangeToDelete : hostRangesToDelete) {
      myDelegate.deleteString(hostRangeToDelete.getStartOffset() + delta, hostRangeToDelete.getEndOffset() + delta);
      delta -= hostRangeToDelete.getLength();
    }
  }

  public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
    if (isOneLine()) {
      s = StringUtil.replace(s.toString(), "\n", "");
    }

    final CharSequence chars = getCharsSequence();
    CharSequence toDelete = chars.subSequence(startOffset, endOffset);

    int perfixLength = StringUtil.commonPrefixLength(s, toDelete);
    int suffixLength = StringUtil.commonSuffixLength(toDelete.subSequence(perfixLength, toDelete.length()), s.subSequence(perfixLength, s.length()));
    startOffset += perfixLength;
    endOffset -= suffixLength;
    s = s.subSequence(perfixLength, s.length() - suffixLength);

    doReplaceString(startOffset, endOffset, s);
  }

  private void doReplaceString(int startOffset, int endOffset, CharSequence s) {
    assert intersectWithEditable(new TextRange(startOffset, startOffset)) != null;
    assert intersectWithEditable(new TextRange(endOffset, endOffset)) != null;

    List<Pair<TextRange,CharSequence>> hostRangesToModify = new ArrayList<Pair<TextRange, CharSequence>>(myShreds.size());

    int offset = startOffset;
    int curRangeStart = 0;
    for (int i = 0; i < myShreds.size(); i++) {
      PsiLanguageInjectionHost.Shred shred = myShreds.get(i);
      curRangeStart += shred.prefix.length();
      if (offset < curRangeStart) offset = curRangeStart;
      RangeMarker hostRange = shred.getHostRangeMarker();
      if (!hostRange.isValid()) continue;
      int hostRangeLength = hostRange.getEndOffset() - hostRange.getStartOffset();
      TextRange range = TextRange.from(curRangeStart, hostRangeLength);
      if (range.contains(offset) || range.getEndOffset() == offset/* in case of inserting at the end*/) {
        TextRange rangeToModify = new TextRange(offset, Math.min(range.getEndOffset(), endOffset));
        TextRange hostRangeToModify = rangeToModify.shiftRight(hostRange.getStartOffset() - curRangeStart);
        CharSequence toReplace = i == myShreds.size() - 1 || range.getEndOffset() + shred.suffix.length() >= endOffset
                                 ? s : s.subSequence(0, Math.min(hostRangeToModify.getLength(), s.length()));
        s = toReplace == s ? "" : s.subSequence(toReplace.length(), s.length());
        hostRangesToModify.add(Pair.create(hostRangeToModify, toReplace));
        offset = rangeToModify.getEndOffset();
      }
      curRangeStart += hostRangeLength;
      curRangeStart += shred.suffix.length();
      if (curRangeStart >= endOffset) break;
    }

    int delta = 0;
    for (Pair<TextRange, CharSequence> pair : hostRangesToModify) {
      TextRange hostRange = pair.getFirst();
      CharSequence replace = pair.getSecond();

      myDelegate.replaceString(hostRange.getStartOffset() + delta, hostRange.getEndOffset() + delta, replace);
      delta -= hostRange.getLength() - replace.length();
    }
  }

  public boolean isWritable() {
    return myDelegate.isWritable();
  }

  public long getModificationStamp() {
    return myDelegate.getModificationStamp();
  }

  public void fireReadOnlyModificationAttempt() {
    myDelegate.fireReadOnlyModificationAttempt();
  }

  public void addDocumentListener(@NotNull final DocumentListener listener) {
    myDelegate.addDocumentListener(listener);
  }

  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
    myDelegate.addDocumentListener(listener, parentDisposable);
  }

  public void removeDocumentListener(@NotNull final DocumentListener listener) {
    myDelegate.removeDocumentListener(listener);
  }

  @NotNull
  public RangeMarker createRangeMarker(final int startOffset, final int endOffset) {
    TextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));
    RangeMarker hostMarker = myDelegate.createRangeMarker(hostRange);
    return new RangeMarkerWindow(this, (RangeMarkerEx)hostMarker);
  }

  @NotNull
  public RangeMarker createRangeMarker(final int startOffset, final int endOffset, final boolean surviveOnExternalChange) {
    if (!surviveOnExternalChange) {
      return createRangeMarker(startOffset, endOffset);
    }
    TextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));
    //todo persistent?
    return myDelegate.createRangeMarker(hostRange.getStartOffset(), hostRange.getEndOffset(), surviveOnExternalChange);
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    //noinspection deprecation
    return new MarkupModelWindow((MarkupModelEx)myDelegate.getMarkupModel(), this);
  }

  @NotNull
  public MarkupModel getMarkupModel(final Project project) {
    return new MarkupModelWindow((MarkupModelEx)myDelegate.getMarkupModel(project), this);
  }

  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myDelegate.removePropertyChangeListener(listener);
  }

  public void setReadOnly(final boolean isReadOnly) {
    myDelegate.setReadOnly(isReadOnly);
  }

  @NotNull
  public RangeMarker createGuardedBlock(final int startOffset, final int endOffset) {
    TextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myDelegate.createGuardedBlock(hostRange.getStartOffset(), hostRange.getEndOffset());
  }

  public void removeGuardedBlock(@NotNull final RangeMarker block) {
    myDelegate.removeGuardedBlock(block);
  }

  public RangeMarker getOffsetGuard(final int offset) {
    return myDelegate.getOffsetGuard(injectedToHost(offset));
  }

  public RangeMarker getRangeGuard(final int startOffset, final int endOffset) {
    TextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));

    return myDelegate.getRangeGuard(hostRange.getStartOffset(), hostRange.getEndOffset());
  }

  public void startGuardedBlockChecking() {
    myDelegate.startGuardedBlockChecking();
  }

  public void stopGuardedBlockChecking() {
    myDelegate.stopGuardedBlockChecking();
  }

  public void setCyclicBufferSize(final int bufferSize) {
    myDelegate.setCyclicBufferSize(bufferSize);
  }

  public void setText(@NotNull CharSequence text) {
    LOG.assertTrue(text.toString().startsWith(myShreds.get(0).prefix));
    LOG.assertTrue(text.toString().endsWith(myShreds.get(myShreds.size() - 1).suffix));
    if (isOneLine()) {
      text = StringUtil.replace(text.toString(), "\n", "");
    }
    String[] changes = calculateMinEditSequence(text.toString());
    assert changes.length == myShreds.size();
    for (int i = 0; i < changes.length; i++) {
      String change = changes[i];
      if (change != null) {
        RangeMarker hostRange = myShreds.get(i).getHostRangeMarker();
        myDelegate.replaceString(hostRange.getStartOffset(), hostRange.getEndOffset(), change);
      }
    }
  }

  @NotNull
  public RangeMarker[] getHostRanges() {
    RangeMarker[] markers = new RangeMarker[myShreds.size()];
    for (int i = 0; i < myShreds.size(); i++) {
      PsiLanguageInjectionHost.Shred shred = myShreds.get(i);
      markers[i] = shred.getHostRangeMarker();
    }
    return markers;
  }

  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    TextRange hostRange = injectedToHost(new ProperTextRange(textRange));
    RangeMarker hostMarker = myDelegate.createRangeMarker(hostRange);
    return new RangeMarkerWindow(this, (RangeMarkerEx)hostMarker);
  }

  public void stripTrailingSpaces(final boolean inChangedLinesOnly) {
    myDelegate.stripTrailingSpaces(inChangedLinesOnly);
  }

  public void setStripTrailingSpacesEnabled(final boolean isEnabled) {
    myDelegate.setStripTrailingSpacesEnabled(isEnabled);
  }

  public int getLineSeparatorLength(final int line) {
    return myDelegate.getLineSeparatorLength(injectedToHostLine(line));
  }

  @NotNull
  public LineIterator createLineIterator() {
    return myDelegate.createLineIterator();
  }

  public void setModificationStamp(final long modificationStamp) {
    myDelegate.setModificationStamp(modificationStamp);
  }

  public void addEditReadOnlyListener(@NotNull final EditReadOnlyListener listener) {
    myDelegate.addEditReadOnlyListener(listener);
  }

  public void removeEditReadOnlyListener(@NotNull final EditReadOnlyListener listener) {
    myDelegate.removeEditReadOnlyListener(listener);
  }

  public void replaceText(@NotNull final CharSequence chars, final long newModificationStamp) {
    setText(chars);
    myDelegate.setModificationStamp(newModificationStamp);
  }

  public int getListenersCount() {
    return myDelegate.getListenersCount();
  }

  public void suppressGuardedExceptions() {
    myDelegate.suppressGuardedExceptions();
  }

  public void unSuppressGuardedExceptions() {
    myDelegate.unSuppressGuardedExceptions();
  }

  public boolean isInEventsHandling() {
    return myDelegate.isInEventsHandling();
  }

  public void clearLineModificationFlags() {
  }

  public void removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    myDelegate.removeRangeMarker(((RangeMarkerWindow)rangeMarker).getDelegate()); 
  }

  public void addRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    myDelegate.addRangeMarker(((RangeMarkerWindow)rangeMarker).getDelegate());
  }

  public boolean isInBulkUpdate() {
    return false;
  }

  public void setInBulkUpdate(boolean value) {
  }

  @NotNull
  public DocumentEx getDelegate() {
    return myDelegate;
  }

  //todo use escaper?
  public int hostToInjected(int hostOffset) {
    if (hostOffset < myShreds.get(0).getHostRangeMarker().getStartOffset()) return myShreds.get(0).prefix.length();
    int offset = 0;
    for (int i = 0; i < myShreds.size(); i++) {
      offset += myShreds.get(i).prefix.length();
      RangeMarker currentRange = myShreds.get(i).getHostRangeMarker();
      RangeMarker nextRange = i == myShreds.size() - 1 ? null : myShreds.get(i + 1).getHostRangeMarker();
      if (nextRange == null || hostOffset < nextRange.getStartOffset()) {
        if (hostOffset >= currentRange.getEndOffset()) hostOffset = currentRange.getEndOffset();
        return offset + hostOffset - currentRange.getStartOffset();
      }
      offset += currentRange.getEndOffset() - currentRange.getStartOffset();
      offset += myShreds.get(i).suffix.length();
    }
    return getTextLength() - myShreds.get(myShreds.size() - 1).suffix.length();
  }

  public int injectedToHost(int offset) {
    int offsetInLeftFragment = injectedToHost(offset, true);
    int offsetInRightFragment = injectedToHost(offset, false);
    if (offsetInLeftFragment == offsetInRightFragment) return offsetInLeftFragment;

    // heuristics: return offset closest to caret
    Editor editor = PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
    if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();
    if (editor != null) {
      int caret = editor.getCaretModel().getOffset();
      return Math.abs(caret - offsetInLeftFragment) < Math.abs(caret - offsetInRightFragment) ? offsetInLeftFragment : offsetInRightFragment;
    }
    return offsetInLeftFragment;
  }

  private int injectedToHost(int offset, boolean preferLeftFragment) {
    if (offset < myShreds.get(0).prefix.length()) return myShreds.get(0).getHostRangeMarker().getStartOffset();
    int prevEnd = 0;
    for (int i = 0; i < myShreds.size(); i++) {
      RangeMarker currentRange = myShreds.get(i).getHostRangeMarker();
      offset -= myShreds.get(i).prefix.length();
      int length = currentRange.getEndOffset() - currentRange.getStartOffset();
      if (offset < 0) {
        return preferLeftFragment ? prevEnd : currentRange.getStartOffset() - 1;
      }
      else if (offset == 0) {
        return preferLeftFragment && i != 0 ? prevEnd : currentRange.getStartOffset();
      }
      else if (offset < length || offset == length && preferLeftFragment) {
        return currentRange.getStartOffset() + offset;
      }
      offset -= length;
      offset -= myShreds.get(i).suffix.length();
      prevEnd = currentRange.getEndOffset();
    }
    return myShreds.get(myShreds.size() - 1).getHostRangeMarker().getEndOffset();
  }

  @NotNull
  public TextRange injectedToHost(@NotNull TextRange injected) {
    ProperTextRange.assertProperRange(injected);
    int start = injectedToHost(injected.getStartOffset(), false);
    int end = injectedToHost(injected.getEndOffset(), true);
    if (end < start) {
      end = injectedToHost(injected.getEndOffset(), false);
    }
    return new ProperTextRange(start, end);
  }

  public int injectedToHostLine(int line) {
    if (line < myPrefixLineCount) {
      return myDelegate.getLineNumber(myShreds.get(0).getHostRangeMarker().getStartOffset());
    }
    int lineCount = getLineCount();
    if (line > lineCount - mySuffixLineCount) {
      return lineCount;
    }
    int offset = getLineStartOffset(line);
    int hostOffset = injectedToHost(offset);

    return myDelegate.getLineNumber(hostOffset);
  }

  public boolean containsRange(int start, int end) {
    if (end - start > myShreds.get(0).getHostRangeMarker().getEndOffset() - myShreds.get(0).getHostRangeMarker().getStartOffset()) return false;
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      RangeMarker hostRange = shred.getHostRangeMarker();
      if (!hostRange.isValid()) continue;
      TextRange textRange = InjectedLanguageUtil.toTextRange(hostRange);
      if (textRange.contains(new ProperTextRange(start, end))) return true;
    }
    return false;
  }

  @Deprecated
  @Nullable
  public TextRange intersectWithEditable(@NotNull TextRange rangeToEdit) {
    int offset = 0;
    int startOffset = -1;
    int endOffset = -1;
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      RangeMarker hostRange = shred.getHostRangeMarker();
      offset += shred.prefix.length();
      int length = hostRange.getEndOffset() - hostRange.getStartOffset();
      TextRange intersection = new ProperTextRange(offset, offset + length).intersection(rangeToEdit);
      if (intersection != null) {
        if (startOffset == -1) {
          startOffset = intersection.getStartOffset();
        }
        endOffset = intersection.getEndOffset();
      }
      offset += length;
      offset += shred.suffix.length();
    }
    if (startOffset == -1) return null;
    return new ProperTextRange(startOffset, endOffset);
  }

  boolean intersects(DocumentWindowImpl documentWindow) {
    int i = 0;
    int j = 0;
    while (i < myShreds.size() && j < documentWindow.myShreds.size()) {
      RangeMarker range = myShreds.get(i).getHostRangeMarker();
      RangeMarker otherRange = documentWindow.myShreds.get(j).getHostRangeMarker();
      if (InjectedLanguageUtil.toTextRange(range).intersects(InjectedLanguageUtil.toTextRange(otherRange))) return true;
      if (range.getEndOffset() > otherRange.getStartOffset()) i++;
      else if (range.getStartOffset() < otherRange.getEndOffset()) j++;
      else {
        i++;
        j++;
      }
    }
    return false;
  }

  // minimum sequence of text replacement operations for each host range
  // result[i] == null means no change
  // result[i] == "" means delete
  // result[i] == string means replace
  public String[] calculateMinEditSequence(String newText) {
    String[] result = new String[myShreds.size()];
    String hostText = myDelegate.getText();
    calculateMinEditSequence(hostText, newText, result, 0, result.length - 1);
    for (int i = 0; i < result.length; i++) {
      String change = result[i];
      if (change == null) continue;
      String prefix = myShreds.get(i).prefix;
      String suffix = myShreds.get(i).suffix;
      assert change.startsWith(prefix) : change + "/" + prefix;
      assert change.endsWith(suffix) : change + "/" + suffix;
      result[i] = StringUtil.trimEnd(StringUtil.trimStart(change, prefix), suffix);
    }
    return result;
  }

  private String getRangeText(String hostText, int hostNum) {
    PsiLanguageInjectionHost.Shred shred = myShreds.get(hostNum);
    return shred.prefix +
           hostText.substring(shred.getHostRangeMarker().getStartOffset(), shred.getHostRangeMarker().getEndOffset()) +
           shred.suffix;
  }
  private void calculateMinEditSequence(String hostText, String newText, String[] result, int i, int j) {
    String rangeText1 = getRangeText(hostText, i);
    if (i == j) {
      result[i] = rangeText1.equals(newText) ? null : newText;
      return;
    }
    if (StringUtil.startsWith(newText, rangeText1)) {
      result[i] = null;  //no change
      calculateMinEditSequence(hostText, newText.substring(rangeText1.length()), result, i+1, j);
      return;
    }
    String rangeText2 = getRangeText(hostText, j);
    if (StringUtil.endsWith(newText, rangeText2)) {
      result[j] = null;  //no change
      calculateMinEditSequence(hostText, newText.substring(0, newText.length() - rangeText2.length()), result, i, j-1);
      return;
    }
    if (i+1 == j) {
      String suffix = myShreds.get(i).suffix;
      String prefix = myShreds.get(j).prefix;
      String separator = suffix + prefix;
      if (separator.length() != 0) {
        int sep = newText.indexOf(separator);
        assert sep != -1;
        result[i] = newText.substring(0, sep + suffix.length());
        result[j] = newText.substring(sep + suffix.length() + prefix.length(), newText.length());
        return;
      }
      String commonPrefix = StringUtil.commonPrefix(rangeText1, newText);
      result[i] = commonPrefix;
      result[j] = newText.substring(commonPrefix.length());
      return;
    }
    String middleText = getRangeText(hostText, i + 1);
    int m = newText.indexOf(middleText);
    if (m != -1) {
      result[i] = newText.substring(0, m);
      result[i+1] = null;
      calculateMinEditSequence(hostText, newText.substring(m+middleText.length(), newText.length()), result, i+2, j);
      return;
    }
    middleText = getRangeText(hostText, j - 1);
    m = newText.lastIndexOf(middleText);
    if (m != -1) {
      result[j] = newText.substring(m+middleText.length());
      result[j-1] = null;
      calculateMinEditSequence(hostText, newText.substring(0, m), result, i, j-2);
      return;
    }
    result[i] = "";
    result[j] = "";
    calculateMinEditSequence(hostText, newText, result, i+1, j-1);
  }

  public boolean areRangesEqual(@NotNull DocumentWindow otherd) {
    DocumentWindowImpl window = (DocumentWindowImpl)otherd;
    if (myShreds.size() != window.myShreds.size()) return false;
    for (int i = 0; i < myShreds.size(); i++) {
      PsiLanguageInjectionHost.Shred shred = myShreds.get(i);
      PsiLanguageInjectionHost.Shred otherShred = window.myShreds.get(i);
      if (!shred.prefix.equals(otherShred.prefix)) return false;
      if (!shred.suffix.equals(otherShred.suffix)) return false;

      RangeMarker hostRange = shred.getHostRangeMarker();
      RangeMarker other = otherShred.getHostRangeMarker();
      if (hostRange.getStartOffset() != other.getStartOffset()) return false;
      if (hostRange.getEndOffset() != other.getEndOffset()) return false;
    }
    return true;
  }

  public boolean isValid() {
    return myShreds.isValid();
  }

  public boolean equals(Object o) {
    if (!(o instanceof DocumentWindowImpl)) return false;
    DocumentWindowImpl window = (DocumentWindowImpl)o;
    return myDelegate.equals(window.getDelegate()) && areRangesEqual(window);
  }

  public int hashCode() {
    return myShreds.get(0).getHostRangeMarker().getStartOffset();
  }

  public boolean isOneLine() {
    return myOneLine;
  }

  public void dispose() {
    for (PsiLanguageInjectionHost.Shred shred : myShreds) {
      RangeMarker rangeMarker = shred.getHostRangeMarker();
      myDelegate.removeRangeMarker((RangeMarkerEx)rangeMarker);
    }
  }

  public void setShreds(Place shreds) {
    myShreds = shreds;
  }

  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return Collections.emptyList();
  }
}
