/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.util.Processor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.injected.editor.DocumentWindowImpl");
  private final DocumentEx myDelegate;
  private final boolean myOneLine;
  private Place myShreds; // guarded by myLock
  private final int myPrefixLineCount;
  private final int mySuffixLineCount;
  private final Object myLock = new Object();

  private CachedText myCachedText = null;

  public DocumentWindowImpl(@NotNull DocumentEx delegate, boolean oneLine, @NotNull Place shreds) {
    myDelegate = delegate;
    myOneLine = oneLine;
    synchronized (myLock) {
      myShreds = shreds;
    }
    myPrefixLineCount = Math.max(1, 1 + StringUtil.countNewLines(shreds.get(0).getPrefix()));
    mySuffixLineCount = Math.max(1, 1 + StringUtil.countNewLines(shreds.get(shreds.size()- 1).getSuffix()));
  }

  @Nullable("null means we were unable to calculate")
  LogicalPosition hostToInjectedInVirtualSpace(@NotNull LogicalPosition hPos) {
    // beware the virtual space
    int hLineStartOffset = hPos.line >= myDelegate.getLineCount() ? myDelegate.getTextLength() : myDelegate.getLineStartOffset(hPos.line);
    int iLineStartOffset = hostToInjected(hLineStartOffset);
    int iLine = getLineNumber(iLineStartOffset);

    synchronized (myLock) {
      for (int i = myShreds.size() - 1; i >= 0; i--) {
        PsiLanguageInjectionHost.Shred shred = myShreds.get(i);
        if (!shred.isValid()) continue;
        Segment hostRangeMarker = shred.getHostRangeMarker();
        if (hostRangeMarker == null) continue;
        int hShredEndOffset = hostRangeMarker.getEndOffset();
        int hShredStartOffset = hostRangeMarker.getStartOffset();

        int hShredStartLine = myDelegate.getLineNumber(hShredStartOffset);
        int hShredEndLine = myDelegate.getLineNumber(hShredEndOffset);

        if (hShredStartLine <= hPos.line && hPos.line <= hShredEndLine) {
          int hColumnOfShredEnd = hShredEndOffset - hLineStartOffset;
          int iColumnOfShredEnd = hostToInjected(hShredEndOffset) - iLineStartOffset;
          int iColumn = iColumnOfShredEnd + hPos.column - hColumnOfShredEnd;
          return new LogicalPosition(iLine, iColumn);
        }
      }
    }

    return null;
  }

  private static class CachedText {
    private final String text;
    private final long modificationStamp;

    private CachedText(@NotNull String text, long modificationStamp) {
      this.text = text;
      this.modificationStamp = modificationStamp;
    }

    @NotNull
    private String getText() {
      return text;
    }

    private long getModificationStamp() {
      return modificationStamp;
    }
  }


  @Override
  public int getLineCount() {
    return 1 + StringUtil.countNewLines(getText());
  }

  @Override
  public int getLineStartOffset(int line) {
    LOG.assertTrue(line >= 0, line);
    if (line == 0) return 0;
    String hostText = myDelegate.getText();

    int[] pos = new int[2]; // pos[0] = curLine; pos[1] == offset;
    synchronized (myLock) {
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        Segment hostRange = shred.getHostRangeMarker();
        if (hostRange == null) continue;

        int found = countNewLinesIn(shred.getPrefix(), pos, line);
        if (found != -1) return found;

        CharSequence text = hostText.subSequence(hostRange.getStartOffset(), hostRange.getEndOffset());
        found = countNewLinesIn(text, pos, line);
        if (found != -1) return found;

        found = countNewLinesIn(shred.getSuffix(), pos, line);
        if (found != -1) return found;
      }
    }

    return pos[1];
  }

  // returns startOffset found, or -1 if need to continue searching
  private static int countNewLinesIn(CharSequence text, int[] pos, int line) {
    int offsetInside = 0;
    for (int i = StringUtil.indexOf(text, '\n'); i != -1; i = StringUtil.indexOf(text,'\n', offsetInside)) {
      int curLine = ++pos[0];
      int lineLength = i + 1 - offsetInside;
      int offset = pos[1] += lineLength;
      offsetInside += lineLength;
      if (curLine == line) return offset;
    }
    pos[1] += text.length() - offsetInside;
    return -1;
  }

  @Override
  public int getLineEndOffset(int line) {
    LOG.assertTrue(line >= 0, line);
    if (line == getLineCount() - 1) return getTextLength(); // end >= start assertion fix for last line
    int startOffsetOfNextLine = getLineStartOffset(line + 1);
    return startOffsetOfNextLine == 0 || getText().charAt(startOffsetOfNextLine - 1) != '\n' ? startOffsetOfNextLine : startOffsetOfNextLine - 1;
  }

  @NotNull
  @Override
  public String getText() {
    CachedText cachedText = myCachedText;

    if (cachedText == null || cachedText.getModificationStamp() != getModificationStamp()) {
      myCachedText = cachedText = new CachedText(calcText(), getModificationStamp());
    }

    return cachedText.getText();
  }

  @NotNull
  private String calcText() {
    StringBuilder text = new StringBuilder();
    CharSequence hostText = myDelegate.getCharsSequence();
    synchronized (myLock) {
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        Segment hostRange = shred.getHostRangeMarker();
        if (hostRange != null) {
          text.append(shred.getPrefix());
          text.append(hostText, hostRange.getStartOffset(), hostRange.getEndOffset());
          text.append(shred.getSuffix());
        }
      }
    }
    return text.toString();
  }

  @NotNull
  @Override
  public String getText(@NotNull TextRange range) {
    return range.substring(getText());
  }

  @Override
  @NotNull
  public CharSequence getCharsSequence() {
    return getText();
  }

  @NotNull
  @Override
  public CharSequence getImmutableCharSequence() {
    return ImmutableCharSequence.asImmutable(getText());
  }

  @Override
  @NotNull
  public char[] getChars() {
    return CharArrayUtil.fromSequence(getText());
  }

  @Override
  public int getTextLength() {
    int length = 0;
    synchronized (myLock) {
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        Segment hostRange = shred.getHostRangeMarker();
        if (hostRange == null) continue;
        length += shred.getPrefix().length();
        length += hostRange.getEndOffset() - hostRange.getStartOffset();
        length += shred.getSuffix().length();
      }
    }
    return length;
  }

  @Override
  public int getLineNumber(int offset) {
    int lineNumber = 0;
    String hostText = myDelegate.getText();
    synchronized (myLock) {
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        String prefix = shred.getPrefix();
        String suffix = shred.getSuffix();
        lineNumber += StringUtil.getLineBreakCount(prefix.substring(0, Math.min(offset, prefix.length())));
        if (offset < prefix.length()) {
          return lineNumber;
        }
        offset -= prefix.length();

        Segment currentRange = shred.getHostRangeMarker();
        if (currentRange == null) continue;
        int rangeLength = currentRange.getEndOffset() - currentRange.getStartOffset();
        CharSequence rangeText = hostText.subSequence(currentRange.getStartOffset(), currentRange.getEndOffset());

        lineNumber += StringUtil.getLineBreakCount(rangeText.subSequence(0, Math.min(offset, rangeLength)));
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
    }
    lineNumber = getLineCount() - 1;
    return lineNumber < 0 ? 0 : lineNumber;
  }

  @Override
  public TextRange getHostRange(int hostOffset) {
    synchronized (myLock) {
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        Segment currentRange = shred.getHostRangeMarker();
        if (currentRange == null) continue;
        TextRange textRange = ProperTextRange.create(currentRange);
        if (textRange.grown(1).contains(hostOffset)) return textRange;
      }
    }
    return null;
  }

  @Override
  public void insertString(final int offset, @NotNull CharSequence s) {
    synchronized (myLock) {
      LOG.assertTrue(offset >= myShreds.get(0).getPrefix().length(), myShreds.get(0).getPrefix());
      LOG.assertTrue(offset <= getTextLength() - myShreds.get(myShreds.size() - 1).getSuffix().length(),
                     myShreds.get(myShreds.size() - 1).getSuffix());
    }
    if (isOneLine()) {
      s = StringUtil.replace(s.toString(), "\n", "");
    }
    myDelegate.insertString(injectedToHost(offset), s);
  }

  @Override
  public void deleteString(final int startOffset, final int endOffset) {
    assert intersectWithEditable(new TextRange(startOffset, startOffset)) != null;
    assert intersectWithEditable(new TextRange(endOffset, endOffset)) != null;

    List<TextRange> hostRangesToDelete;
    synchronized (myLock) {
      hostRangesToDelete = new ArrayList<TextRange>(myShreds.size());

      int offset = startOffset;
      int curRangeStart = 0;
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        curRangeStart += shred.getPrefix().length();
        if (offset < curRangeStart) offset = curRangeStart;
        if (offset >= endOffset) break;
        Segment hostRange = shred.getHostRangeMarker();
        if (hostRange == null) continue;
        int hostRangeLength = hostRange.getEndOffset() - hostRange.getStartOffset();
        TextRange range = TextRange.from(curRangeStart, hostRangeLength);
        if (range.contains(offset)) {
          TextRange rangeToDelete = new TextRange(offset, Math.min(range.getEndOffset(), endOffset));
          hostRangesToDelete.add(rangeToDelete.shiftRight(hostRange.getStartOffset() - curRangeStart));
          offset = rangeToDelete.getEndOffset();
        }
        curRangeStart += hostRangeLength;
        curRangeStart += shred.getSuffix().length();
      }
    }

    int delta = 0;
    for (TextRange hostRangeToDelete : hostRangesToDelete) {
      myDelegate.deleteString(hostRangeToDelete.getStartOffset() + delta, hostRangeToDelete.getEndOffset() + delta);
      delta -= hostRangeToDelete.getLength();
    }
  }

  @Override
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

    List<Pair<TextRange,CharSequence>> hostRangesToModify;
    synchronized (myLock) {
      hostRangesToModify = new ArrayList<Pair<TextRange, CharSequence>>(myShreds.size());

      int offset = startOffset;
      int curRangeStart = 0;
      for (int i = 0; i < myShreds.size(); i++) {
        PsiLanguageInjectionHost.Shred shred = myShreds.get(i);
        curRangeStart += shred.getPrefix().length();
        if (offset < curRangeStart) offset = curRangeStart;
        Segment hostRange = shred.getHostRangeMarker();
        if (hostRange == null) continue;
        int hostRangeLength = hostRange.getEndOffset() - hostRange.getStartOffset();
        TextRange range = TextRange.from(curRangeStart, hostRangeLength);
        if (range.contains(offset) || range.getEndOffset() == offset/* in case of inserting at the end*/) {
          TextRange rangeToModify = new TextRange(offset, Math.min(range.getEndOffset(), endOffset));
          TextRange hostRangeToModify = rangeToModify.shiftRight(hostRange.getStartOffset() - curRangeStart);
          CharSequence toReplace = i == myShreds.size() - 1 || range.getEndOffset() + shred.getSuffix().length() >= endOffset
                                   ? s : s.subSequence(0, Math.min(hostRangeToModify.getLength(), s.length()));
          s = toReplace == s ? "" : s.subSequence(toReplace.length(), s.length());
          hostRangesToModify.add(Pair.create(hostRangeToModify, toReplace));
          offset = rangeToModify.getEndOffset();
        }
        curRangeStart += hostRangeLength;
        curRangeStart += shred.getSuffix().length();
        if (curRangeStart >= endOffset) break;
      }
    }

    int delta = 0;
    for (Pair<TextRange, CharSequence> pair : hostRangesToModify) {
      TextRange hostRange = pair.getFirst();
      CharSequence replace = pair.getSecond();

      myDelegate.replaceString(hostRange.getStartOffset() + delta, hostRange.getEndOffset() + delta, replace);
      delta -= hostRange.getLength() - replace.length();
    }
  }

  @Override
  public void moveText(int srcStart, int srcEnd, int dstOffset) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWritable() {
    return myDelegate.isWritable();
  }

  @Override
  public long getModificationStamp() {
    return isValid() ? myDelegate.getModificationStamp() : -1;
  }

  @Override
  public int getModificationSequence() {
    return myDelegate.getModificationSequence();
  }

  @Override
  public void fireReadOnlyModificationAttempt() {
    myDelegate.fireReadOnlyModificationAttempt();
  }

  @Override
  public void addDocumentListener(@NotNull final DocumentListener listener) {
    myDelegate.addDocumentListener(listener);
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) {
    myDelegate.addDocumentListener(listener, parentDisposable);
  }

  @Override
  public void removeDocumentListener(@NotNull final DocumentListener listener) {
    myDelegate.removeDocumentListener(listener);
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(final int startOffset, final int endOffset) {
    ProperTextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));
    RangeMarker hostMarker = myDelegate.createRangeMarker(hostRange);
    int startShift = Math.max(0, hostToInjected(hostRange.getStartOffset()) - startOffset);
    int endShift = Math.max(0, endOffset - hostToInjected(hostRange.getEndOffset()) - startShift);
    return new RangeMarkerWindow(this, (RangeMarkerEx)hostMarker, startShift, endShift);
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(final int startOffset, final int endOffset, final boolean surviveOnExternalChange) {
    if (!surviveOnExternalChange) {
      return createRangeMarker(startOffset, endOffset);
    }
    ProperTextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));
    //todo persistent?
    RangeMarker hostMarker = myDelegate.createRangeMarker(hostRange.getStartOffset(), hostRange.getEndOffset(), surviveOnExternalChange);
    int startShift = Math.max(0, hostToInjected(hostRange.getStartOffset()) - startOffset);
    int endShift = Math.max(0, endOffset - hostToInjected(hostRange.getEndOffset()) - startShift);
    return new RangeMarkerWindow(this, (RangeMarkerEx)hostMarker, startShift, endShift);
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myDelegate.removePropertyChangeListener(listener);
  }

  @Override
  public void setReadOnly(final boolean isReadOnly) {
    myDelegate.setReadOnly(isReadOnly);
  }

  @Override
  @NotNull
  public RangeMarker createGuardedBlock(final int startOffset, final int endOffset) {
    ProperTextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myDelegate.createGuardedBlock(hostRange.getStartOffset(), hostRange.getEndOffset());
  }

  @Override
  public void removeGuardedBlock(@NotNull final RangeMarker block) {
    myDelegate.removeGuardedBlock(block);
  }

  @Override
  public RangeMarker getOffsetGuard(final int offset) {
    return myDelegate.getOffsetGuard(injectedToHost(offset));
  }

  @Override
  public RangeMarker getRangeGuard(final int startOffset, final int endOffset) {
    ProperTextRange hostRange = injectedToHost(new ProperTextRange(startOffset, endOffset));

    return myDelegate.getRangeGuard(hostRange.getStartOffset(), hostRange.getEndOffset());
  }

  @Override
  public void startGuardedBlockChecking() {
    myDelegate.startGuardedBlockChecking();
  }

  @Override
  public void stopGuardedBlockChecking() {
    myDelegate.stopGuardedBlockChecking();
  }

  @Override
  public void setCyclicBufferSize(final int bufferSize) {
    myDelegate.setCyclicBufferSize(bufferSize);
  }

  @Override
  public void setText(@NotNull CharSequence text) {
    synchronized (myLock) {
      LOG.assertTrue(text.toString().startsWith(myShreds.get(0).getPrefix()));
      LOG.assertTrue(text.toString().endsWith(myShreds.get(myShreds.size() - 1).getSuffix()));
      if (isOneLine()) {
        text = StringUtil.replace(text.toString(), "\n", "");
      }
      String[] changes = calculateMinEditSequence(text.toString());
      assert changes.length == myShreds.size();
      for (int i = 0; i < changes.length; i++) {
        String change = changes[i];
        if (change != null) {
          Segment hostRange = myShreds.get(i).getHostRangeMarker();
          if (hostRange == null) continue;
          myDelegate.replaceString(hostRange.getStartOffset(), hostRange.getEndOffset(), change);
        }
      }
    }
  }

  @Override
  @NotNull
  public Segment[] getHostRanges() {
    synchronized (myLock) {
      List<Segment> markers = new ArrayList<Segment>(myShreds.size());
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        Segment hostMarker = shred.getHostRangeMarker();
        if (hostMarker != null) {
          markers.add(hostMarker);
        }
      }
      return markers.isEmpty() ? Segment.EMPTY_ARRAY : markers.toArray(new Segment[markers.size()]);
    }
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final TextRange textRange) {
    final ProperTextRange properTextRange = new ProperTextRange(textRange);
    return createRangeMarker(properTextRange.getStartOffset(), properTextRange.getEndOffset());
  }

  @Override
  public void setStripTrailingSpacesEnabled(final boolean isEnabled) {
    myDelegate.setStripTrailingSpacesEnabled(isEnabled);
  }

  @Override
  public int getLineSeparatorLength(final int line) {
    return myDelegate.getLineSeparatorLength(injectedToHostLine(line));
  }

  @Override
  @NotNull
  public LineIterator createLineIterator() {
    return myDelegate.createLineIterator();
  }

  @Override
  public void setModificationStamp(final long modificationStamp) {
    myDelegate.setModificationStamp(modificationStamp);
  }

  @Override
  public void addEditReadOnlyListener(@NotNull final EditReadOnlyListener listener) {
    myDelegate.addEditReadOnlyListener(listener);
  }

  @Override
  public void removeEditReadOnlyListener(@NotNull final EditReadOnlyListener listener) {
    myDelegate.removeEditReadOnlyListener(listener);
  }

  @Override
  public void replaceText(@NotNull final CharSequence chars, final long newModificationStamp) {
    setText(chars);
    myDelegate.setModificationStamp(newModificationStamp);
  }

  @Override
  public int getListenersCount() {
    return myDelegate.getListenersCount();
  }

  @Override
  public void suppressGuardedExceptions() {
    myDelegate.suppressGuardedExceptions();
  }

  @Override
  public void unSuppressGuardedExceptions() {
    myDelegate.unSuppressGuardedExceptions();
  }

  @Override
  public boolean isInEventsHandling() {
    return myDelegate.isInEventsHandling();
  }

  @Override
  public void clearLineModificationFlags() {
  }

  @Override
  public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) {
    return myDelegate.removeRangeMarker(((RangeMarkerWindow)rangeMarker).getDelegate()); 
  }

  @Override
  public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                  int start,
                                  int end,
                                  boolean greedyToLeft,
                                  boolean greedyToRight,
                                  int layer) {
    throw new IllegalStateException();
  }

  @Override
  public boolean isInBulkUpdate() {
    return false;
  }

  @Override
  public void setInBulkUpdate(boolean value) {
  }

  @Override
  @NotNull
  public DocumentEx getDelegate() {
    return myDelegate;
  }

  @Override
  public int hostToInjected(int hostOffset) {
    synchronized (myLock) {
      Segment hostRangeMarker = myShreds.get(0).getHostRangeMarker();
      if (hostRangeMarker == null || hostOffset < hostRangeMarker.getStartOffset()) return myShreds.get(0).getPrefix().length();
      int offset = 0;
      for (int i = 0; i < myShreds.size(); i++) {
        offset += myShreds.get(i).getPrefix().length();
        Segment currentRange = myShreds.get(i).getHostRangeMarker();
        if (currentRange == null) continue;
        Segment nextRange = i == myShreds.size() - 1 ? null : myShreds.get(i + 1).getHostRangeMarker();
        if (nextRange == null || hostOffset < nextRange.getStartOffset()) {
          if (hostOffset >= currentRange.getEndOffset()) hostOffset = currentRange.getEndOffset();
          return offset + hostOffset - currentRange.getStartOffset();
        }
        offset += currentRange.getEndOffset() - currentRange.getStartOffset();
        offset += myShreds.get(i).getSuffix().length();
      }
      return getTextLength() - myShreds.get(myShreds.size() - 1).getSuffix().length();
    }
  }

  public int hostToInjectedUnescaped(int hostOffset) {
    synchronized (myLock) {
      Segment hostRangeMarker = myShreds.get(0).getHostRangeMarker();
      if (hostRangeMarker == null || hostOffset < hostRangeMarker.getStartOffset()) return myShreds.get(0).getPrefix().length();
      int offset = 0;
      for (int i = 0; i < myShreds.size(); i++) {
        offset += myShreds.get(i).getPrefix().length();
        Segment currentRange = myShreds.get(i).getHostRangeMarker();
        if (currentRange == null) continue;
        Segment nextRange = i == myShreds.size() - 1 ? null : myShreds.get(i + 1).getHostRangeMarker();
        if (nextRange == null || hostOffset < nextRange.getStartOffset()) {
          if (hostOffset >= currentRange.getEndOffset()) {
            offset += myShreds.get(i).getRange().getLength();
          }
          else {
            //todo use escaper to convert host-range delta into injected space
            offset += hostOffset - currentRange.getStartOffset();
          }
          return offset;
        }
        offset += myShreds.get(i).getRange().getLength();
        offset += myShreds.get(i).getSuffix().length();
      }
      return getTextLength() - myShreds.get(myShreds.size() - 1).getSuffix().length();
    }
  }

  @Override
  public int injectedToHost(int offset) {
    int offsetInLeftFragment = injectedToHost(offset, true);
    int offsetInRightFragment = injectedToHost(offset, false);
    if (offsetInLeftFragment == offsetInRightFragment) return offsetInLeftFragment;

    // heuristics: return offset closest to the caret
    Editor[] editors = EditorFactory.getInstance().getEditors(getDelegate());
    Editor editor  = editors.length == 0 ? null : editors[0];
    if (editor != null)
    {
      if (editor instanceof EditorWindow) editor = ((EditorWindow)editor).getDelegate();
      int caret = editor.getCaretModel().getOffset();
      return Math.abs(caret - offsetInLeftFragment) < Math.abs(caret - offsetInRightFragment) ? offsetInLeftFragment : offsetInRightFragment;
    }
    return offsetInLeftFragment;
  }

  private int injectedToHost(int offset, boolean preferLeftFragment) {
    synchronized (myLock) {
      if (offset < myShreds.get(0).getPrefix().length()) {
        Segment hostRangeMarker = myShreds.get(0).getHostRangeMarker();
        return hostRangeMarker == null ? 0 : hostRangeMarker.getStartOffset();
      }
      int prevEnd = 0;
      for (int i = 0; i < myShreds.size(); i++) {
        Segment currentRange = myShreds.get(i).getHostRangeMarker();
        if (currentRange == null) continue;
        offset -= myShreds.get(i).getPrefix().length();
        int length = currentRange.getEndOffset() - currentRange.getStartOffset();
        if (offset < 0) {
          return preferLeftFragment ? prevEnd : currentRange.getStartOffset() - 1;
        }
        if (offset == 0) {
          return preferLeftFragment && i != 0 ? prevEnd : currentRange.getStartOffset();
        }
        if (offset < length || offset == length && preferLeftFragment) {
          return currentRange.getStartOffset() + offset;
        }
        offset -= length;
        offset -= myShreds.get(i).getSuffix().length();
        prevEnd = currentRange.getEndOffset();
      }
      Segment hostRangeMarker = myShreds.get(myShreds.size() - 1).getHostRangeMarker();
      return hostRangeMarker == null ? 0 : hostRangeMarker.getEndOffset();
    }
  }

  @Override
  @NotNull
  public ProperTextRange injectedToHost(@NotNull TextRange injected) {
    int start = injectedToHost(injected.getStartOffset(), false);
    int end = injectedToHost(injected.getEndOffset(), true);
    if (end < start) {
      end = injectedToHost(injected.getEndOffset(), false);
    }
    return new ProperTextRange(start, end);
  }

  @Override
  public int injectedToHostLine(int line) {
    if (line < myPrefixLineCount) {
      synchronized (myLock) {
        Segment hostRangeMarker = myShreds.get(0).getHostRangeMarker();
        return hostRangeMarker == null ? 0 : myDelegate.getLineNumber(hostRangeMarker.getStartOffset());
      }
    }
    int lineCount = getLineCount();
    if (line > lineCount - mySuffixLineCount) {
      return lineCount;
    }
    int offset = getLineStartOffset(line);
    int hostOffset = injectedToHost(offset);

    return myDelegate.getLineNumber(hostOffset);
  }

  @Override
  public boolean containsRange(int start, int end) {
    synchronized (myLock) {
      ProperTextRange query = new ProperTextRange(start, end);
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        Segment hostRange = shred.getHostRangeMarker();
        if (hostRange == null) continue;
        TextRange textRange = ProperTextRange.create(hostRange);
        if (textRange.contains(query)) return true;
      }
      return false;
    }
  }

  @Override
  @Deprecated
  @Nullable
  public TextRange intersectWithEditable(@NotNull TextRange rangeToEdit) {
    int startOffset = -1;
    int endOffset = -1;
    synchronized (myLock) {
      int offset = 0;
      for (PsiLanguageInjectionHost.Shred shred : myShreds) {
        Segment hostRange = shred.getHostRangeMarker();
        if (hostRange == null) continue;
        offset += shred.getPrefix().length();
        int length = hostRange.getEndOffset() - hostRange.getStartOffset();
        TextRange intersection = new ProperTextRange(offset, offset + length).intersection(rangeToEdit);
        if (intersection != null) {
          if (startOffset == -1) {
            startOffset = intersection.getStartOffset();
          }
          endOffset = intersection.getEndOffset();
        }
        offset += length;
        offset += shred.getSuffix().length();
      }
    }
    if (startOffset == -1) return null;
    return new ProperTextRange(startOffset, endOffset);
  }

  // minimum sequence of text replacement operations for each host range
  // result[i] == null means no change
  // result[i] == "" means delete
  // result[i] == string means replace
  public String[] calculateMinEditSequence(String newText) {
    synchronized (myLock) {
      String[] result = new String[myShreds.size()];
      String hostText = myDelegate.getText();
      calculateMinEditSequence(hostText, newText, result, 0, result.length - 1);
      for (int i = 0; i < result.length; i++) {
        String change = result[i];
        if (change == null) continue;
        String prefix = myShreds.get(i).getPrefix();
        String suffix = myShreds.get(i).getSuffix();
        assert change.startsWith(prefix) : change + "/" + prefix;
        assert change.endsWith(suffix) : change + "/" + suffix;
        result[i] = StringUtil.trimEnd(StringUtil.trimStart(change, prefix), suffix);
      }
      return result;
    }
  }

  private String getRangeText(@NotNull String hostText, int hostNum) {
    synchronized (myLock) {
      PsiLanguageInjectionHost.Shred shred = myShreds.get(hostNum);
      Segment hostRangeMarker = shred.getHostRangeMarker();
      return shred.getPrefix() +
             (hostRangeMarker == null ? "" : hostText.substring(hostRangeMarker.getStartOffset(), hostRangeMarker.getEndOffset())) +
             shred.getSuffix();
    }
  }
  private void calculateMinEditSequence(String hostText, String newText, String[] result, int i, int j) {
    synchronized (myLock) {
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
        String suffix = myShreds.get(i).getSuffix();
        String prefix = myShreds.get(j).getPrefix();
        String separator = suffix + prefix;
        if (!separator.isEmpty()) {
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
  }

  @Override
  public boolean areRangesEqual(@NotNull DocumentWindow otherd) {
    DocumentWindowImpl window = (DocumentWindowImpl)otherd;
    Place shreds = getShreds();
    Place otherShreds = window.getShreds();
    if (shreds.size() != otherShreds.size()) return false;
    for (int i = 0; i < shreds.size(); i++) {
      PsiLanguageInjectionHost.Shred shred = shreds.get(i);
      PsiLanguageInjectionHost.Shred otherShred = otherShreds.get(i);
      if (!shred.getPrefix().equals(otherShred.getPrefix())) return false;
      if (!shred.getSuffix().equals(otherShred.getSuffix())) return false;

      Segment hostRange = shred.getHostRangeMarker();
      Segment other = otherShred.getHostRangeMarker();
      if (hostRange == null || other == null || hostRange.getStartOffset() != other.getStartOffset()) return false;
      if (hostRange.getEndOffset() != other.getEndOffset()) return false;
    }
    return true;
  }

  @Override
  public boolean isValid() {
    PsiLanguageInjectionHost.Shred[] shreds;
    synchronized (myLock) {
      shreds = myShreds.toArray(new PsiLanguageInjectionHost.Shred[myShreds.size()]);
    }
    // can grab PsiLock in SmartPsiPointer.restore()
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      if (!shred.isValid()) return false;
    }
    return true;
  }

  public boolean equals(Object o) {
    if (!(o instanceof DocumentWindowImpl)) return false;
    DocumentWindowImpl window = (DocumentWindowImpl)o;
    return myDelegate.equals(window.getDelegate()) && areRangesEqual(window);
  }

  public int hashCode() {
    synchronized (myLock) {
      Segment hostRangeMarker = myShreds.get(0).getHostRangeMarker();
      return hostRangeMarker == null ? -1 : hostRangeMarker.getStartOffset();
    }
  }

  public boolean isOneLine() {
    return myOneLine;
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      myShreds.dispose();
    }
  }

  public void setShreds(@NotNull Place shreds) {
    synchronized (myLock) {
      myShreds.dispose();
      myShreds = shreds;
    }
  }

  @NotNull
  public Place getShreds() {
    synchronized (myLock) {
      return myShreds;
    }
  }

  @Override
  @NotNull
  public List<RangeMarker> getGuardedBlocks() {
    return Collections.emptyList();
  }

  //todo convert injected RMs to host
  @Override
  public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) {
    return myDelegate.processRangeMarkers(processor);
  }

  @Override
  public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) {
    return myDelegate.processRangeMarkersOverlappingWith(start, end, processor);
  }
}
