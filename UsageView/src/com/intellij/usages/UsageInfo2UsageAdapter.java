package com.intellij.usages;

import com.intellij.ide.EditorHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.FileHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.rules.*;
import com.intellij.util.Icons;
import com.intellij.util.text.CharArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004
 * Time: 2:42:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageInfo2UsageAdapter implements Usage, UsageInModule, UsageInLibrary, UsageInFile, PsiElementUsage, MergeableUsage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.UsageInfo2UsageAdapter");

  private final UsageInfo myUsageInfo;
  private int myLineNumber;
  private int myColumnNumber;
  private PsiFile myPsiFile;
  private Icon myIcon;
  private List<RangeMarker> myRangeMarkers = new ArrayList<RangeMarker>();
  private boolean myShowReadAccessIcon;
  private boolean myShowWriteAccessIcon;
  private TextChunk[] myTextChunks;
  private Document myDocument;
  private EditorColorsScheme myColorsScheme;

  public UsageInfo2UsageAdapter(UsageInfo usageInfo) {
    myColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    myUsageInfo = usageInfo;
    PsiElement element = myUsageInfo.getElement();
    myPsiFile = element.getContainingFile();
    Project project = element.getProject();
    myDocument = PsiDocumentManager.getInstance(project).getDocument(myPsiFile);

    myShowReadAccessIcon = false; // TODO:
    myShowWriteAccessIcon = false;

    TextRange range = element.getTextRange();
    int startOffset = range.getStartOffset() + usageInfo.startOffset;
    int endOffset = range.getStartOffset() + usageInfo.endOffset;

    myLineNumber = myDocument.getLineNumber(startOffset);

    int lineStartOffset = myDocument.getLineStartOffset(myLineNumber);
    myColumnNumber = startOffset - lineStartOffset;

    LOG.assertTrue(endOffset <= myDocument.getTextLength(), "Invalid usage info, psiElement:" + usageInfo.getElement()
                                                            + " end offset: " + endOffset + " psiFile: " + myPsiFile.getName());

    myRangeMarkers.add(myDocument.createRangeMarker(startOffset, endOffset));

    if (element instanceof PsiFile) {
      myIcon = null;
    }
    else {
      myIcon = element.getIcon(0);
      if (myIcon == null) {
        if (myShowReadAccessIcon || myShowWriteAccessIcon) {
          if (myShowWriteAccessIcon) {
            myIcon = Icons.VARIABLE_WRITE_ACCESS;           // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
          }
          else {
            myIcon = Icons.VARIABLE_READ_ACCESS;            // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
          }
        }
      }
    }

    initChunks();
  }

  private void initChunks() {
    int lineStartOffset = myDocument.getLineStartOffset(myLineNumber);
    int lineEndOffset = myDocument.getLineEndOffset(myLineNumber);
    myTextChunks = createTextChunks(myPsiFile.getFileType().getHighlighter(myPsiFile.getProject()), lineStartOffset, lineEndOffset);
  }

  public UsagePresentation getPresentation() {
    return new UsagePresentation() {
      public TextChunk[] getText() {
        return myTextChunks;
      }

      public Icon getIcon() {
        return myIcon;
      }
    };
  }

  public boolean isValid() {
    if (myUsageInfo.getElement() == null) return false;
    for (int i = 0; i < myRangeMarkers.size(); i++) {
      RangeMarker rangeMarker = myRangeMarkers.get(i);
      if (!rangeMarker.isValid()) return false;
    }
    return true;
  }

  public boolean isReadOnly() {
    return isValid() && !myUsageInfo.getElement().isWritable();
  }

  public FileEditorLocation getLocation() {
    VirtualFile virtualFile = myPsiFile.getVirtualFile();
    if (virtualFile == null) return null;
    FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
    if (editor == null) return null;

    return new TextEditorLocation(myUsageInfo.startOffset + myUsageInfo.getElement().getTextRange().getStartOffset(), (TextEditor)editor);
  }

  public void selectInEditor() {
    if (!isValid()) return;
    Editor editor = openTextEditor(false);
    RangeMarker marker = myRangeMarkers.get(0);
    editor.getSelectionModel().setSelection(marker.getStartOffset(), marker.getEndOffset());
  }

  public void highlightInEditor() {
    if (!isValid()) return;

    RangeMarker marker = myRangeMarkers.get(0);
    EditorHighlighter.getInstance(getProject())
      .selectInEditor(getFile(), marker.getStartOffset(), marker.getEndOffset(), false, false);
  }

  public void navigate(boolean focus) {
    openTextEditor(focus);
  }

  private Editor openTextEditor(boolean focus) {
    Project project = myPsiFile.getProject();
    return FileEditorManager.getInstance(project).openTextEditor(getDescriptor(), focus);
  }

  public boolean canNavigate() {
    return getDescriptor().canNavigate();
  }


  private OpenFileDescriptor getDescriptor() {
    return isValid() ? new OpenFileDescriptor(getProject(), getFile(), myRangeMarkers.get(0).getStartOffset()) : null;
  }

  private Project getProject() {
    Project project = myPsiFile.getProject();
    return project;
  }

  public int compareTo(Usage usage) {
    return 0;
  }

  public String toString() {
    TextChunk[] textChunks = getPresentation().getText();
    StringBuffer result = new StringBuffer();
    for (int j = 0; j < textChunks.length; j++) {
      if (j > 0) result.append("|");
      TextChunk textChunk = textChunks[j];
      result.append(textChunk);
    }

    return result.toString();
  }

  public Module getModule() {
    if (!isValid()) return null;
    PsiElement element = myUsageInfo.getElement();
    VirtualFile virtualFile = getFile();
    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(element.getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    Module module = fileIndex.getModuleForFile(virtualFile);
    return module;
  }

  public OrderEntry getLibraryEntry() {
    if (!isValid()) return null;
    PsiElement element = myUsageInfo.getElement();
    PsiFile psiFile = element.getContainingFile();
    VirtualFile virtualFile = getFile();
    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(element.getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    if (psiFile instanceof PsiCompiledElement || fileIndex.isInLibrarySource(virtualFile)) {
      OrderEntry[] orders = fileIndex.getOrderEntriesForFile(psiFile.getVirtualFile());
      for (int i = 0; i < orders.length; i++) {
        OrderEntry order = orders[i];
        if (order instanceof LibraryOrderEntry || order instanceof JdkOrderEntry) {
          return order;
        }
      }
    }
    
    return null;
  }

  public VirtualFile getFile() {
    return isValid() ? myUsageInfo.getElement().getContainingFile().getVirtualFile() : null;
  }

  public int getLine() {
    return myLineNumber;
  }

  public boolean merge(MergeableUsage other) {
    if (!(other instanceof UsageInfo2UsageAdapter)) return false;
    UsageInfo2UsageAdapter u2 = (UsageInfo2UsageAdapter)other;
    if (myLineNumber != u2.myLineNumber || getFile() != u2.getFile()) return false;
    myRangeMarkers.addAll(u2.myRangeMarkers);
    initChunks();
    return true;
  }

  public void reset() {
    if (myRangeMarkers.size() > 1) {
      RangeMarker marker = myRangeMarkers.get(0);
      myRangeMarkers = new ArrayList<RangeMarker>();
      myRangeMarkers.add(marker);
      initChunks();
    }
  }

  private TextChunk[] createTextChunks(FileHighlighter highlighter, int start, int end) {
    LOG.assertTrue(start <= end);
    List<TextChunk> result = new ArrayList<TextChunk>();

    appendPrefix(result);

    Lexer lexer = highlighter.getHighlightingLexer();
    CharSequence chars = myDocument.getCharsSequence();
    lexer.start(CharArrayUtil.fromSequence(chars));

    for (int offset = start; offset < end; offset++) {
      if (chars.charAt(offset) == '\n') {
        end = offset;
        break;
      }
    }

    boolean isBeginning = true;

    while (lexer.getTokenType() != null) {
      try {
        int hiStart = lexer.getTokenStart();
        int hiEnd = lexer.getTokenEnd();

        if (hiStart >= end) break;

        hiStart = Math.max(hiStart, start);
        hiEnd = Math.min(hiEnd, end);
        if (hiStart >= hiEnd) { continue; }

        String text = chars.subSequence(hiStart, hiEnd).toString();
        if (isBeginning && text.trim().length() == 0) continue;
        isBeginning = false;
        IElementType tokenType = lexer.getTokenType();
        TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);

        RangeMarker intersectionMarker = getIntersectingMarker(hiStart, hiEnd);
        if (intersectionMarker != null) {
          processIntersectingRage(chars, hiStart, hiEnd, tokenHighlights, result, intersectionMarker);
        }
        else {
          result.add(new TextChunk(convertAttributes(tokenHighlights), text));
        }
      }
      finally {
        lexer.advance();
      }
    }

    return result.toArray(new TextChunk[result.size()]);
  }

  private RangeMarker getIntersectingMarker(int hiStart, int hiEnd) {
    for (int i = 0; i < myRangeMarkers.size(); i++) {
      RangeMarker marker = myRangeMarkers.get(i);
      if (marker.isValid() && rangeIntersect(hiStart, hiEnd, marker.getStartOffset(), marker.getEndOffset())) return marker;
    }
    return null;
  }

  private void processIntersectingRage(CharSequence chars,
                                       int hiStart,
                                       int hiEnd,
                                       TextAttributesKey[] tokenHighlights,
                                       List<TextChunk> result,
                                       RangeMarker rangeMarker) {
    int usageStart = rangeMarker.getStartOffset();
    int usageEnd = rangeMarker.getEndOffset();

    TextAttributes originalAttrs = convertAttributes(tokenHighlights);
    addChunk(chars, hiStart, Math.max(hiStart, usageStart), originalAttrs, false, result);
    addChunk(chars, Math.max(hiStart, usageStart), Math.min(hiEnd, usageEnd), originalAttrs, true, result);
    addChunk(chars, Math.min(hiEnd, usageEnd), hiEnd, originalAttrs, false, result);
  }

  private void addChunk(CharSequence chars, int start, int end, TextAttributes originalAttrs, boolean bold, List<TextChunk> result) {
    if (start >= end) return;
    String rText = chars.subSequence(start, end).toString();
    TextAttributes attrs = bold
                           ? TextAttributes.merge(originalAttrs, new TextAttributes(null, null, null, null, Font.BOLD))
                           : originalAttrs;
    result.add(new TextChunk(attrs, rText));
  }

  private static boolean rangeIntersect(int s1, int e1, int s2, int e2) {
    return s2 < s1 && s1 < e2 || s2 < e1 && e1 < e2
           || s1 < s2 && s2 < e1 || s1 < e2 && e2 < e1
           || s1 == s2 && e1 == e2;
  }

  private TextAttributes convertAttributes(TextAttributesKey[] keys) {
    TextAttributes attrs = myColorsScheme.getAttributes(HighlighterColors.TEXT);

    for (int i = 0; i < keys.length; i++) {
      TextAttributesKey key = keys[i];
      TextAttributes attrs2 = myColorsScheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }

    attrs = attrs.clone();
    attrs.setFontType(Font.PLAIN);
    return attrs;
  }

  private void appendPrefix(List<TextChunk> result) {
    StringBuffer buffer = new StringBuffer("(");
    buffer.append(myLineNumber + 1);
    buffer.append(", ");
    buffer.append(myColumnNumber + 1);
    buffer.append(") ");
    TextChunk prefixChunk = new TextChunk(myColorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION), buffer.toString());
    result.add(prefixChunk);
  }

  public PsiElement getElement() {
    return myUsageInfo.getElement();
  }

  public boolean isNonCodeUsage() {
    return myUsageInfo.isNonCodeUsage;
  }

  public static Usage[] convert(UsageInfo[] usageInfos) {
    Usage[] usages = new Usage[usageInfos.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = new UsageInfo2UsageAdapter(usageInfos[i]);
    }

    return usages;
  }

  public UsageInfo getUsageInfo() {
    return myUsageInfo;
  }
}
