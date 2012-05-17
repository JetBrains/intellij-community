/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.slice;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SliceHandler;
import com.intellij.slicer.SliceManager;
import com.intellij.slicer.SliceUsage;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.IntArrayList;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;

import java.util.*;

/**
 * @author cdr
 */
public class SliceBackwardTest extends DaemonAnalyzerTestCase {
  private final TIntObjectHashMap<IntArrayList> myFlownOffsets = new TIntObjectHashMap<IntArrayList>();

  private void dotest() throws Exception {
    configureByFile("/codeInsight/slice/backward/"+getTestName(false)+".java");
    Map<String, RangeMarker> sliceUsageName2Offset = extractSliceOffsetsFromDocument(getEditor().getDocument());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = new SliceHandler(true).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    calcRealOffsets(element, sliceUsageName2Offset, myFlownOffsets);
    Collection<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = true;

    SliceUsage usage = SliceManager.createRootUsage(element, params);
    checkUsages(usage, true, myFlownOffsets);
  }

  static void checkUsages(final SliceUsage usage, final boolean dataFlowToThis, final TIntObjectHashMap<IntArrayList> flownOffsets) {
    final List<SliceUsage> children = new ArrayList<SliceUsage>();
    boolean b = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        usage.processChildren(new CommonProcessors.CollectProcessor<SliceUsage>(children));
      }
    }, "Expanding", true, usage.getElement().getProject());
    assertTrue(b);
    int startOffset = usage.getElement().getTextOffset();
    IntArrayList list = flownOffsets.get(startOffset);
    int[] offsets = list == null ? new int[0] : list.toArray();
    Arrays.sort(offsets);

    int size = list == null ? 0 : list.size();
    assertEquals(message(startOffset, usage), size, children.size());
    Collections.sort(children, new Comparator<SliceUsage>() {
      @Override
      public int compare(SliceUsage o1, SliceUsage o2) {
        return o1.compareTo(o2);
      }
    });

    for (int i = 0; i < children.size(); i++) {
      SliceUsage child = children.get(i);
      int offset = offsets[i];
      assertEquals(message(offset, child), offset, child.getUsageInfo().getElement().getTextOffset());

      checkUsages(child, dataFlowToThis, flownOffsets);
    }
  }

  private static String message(int startOffset, SliceUsage usage) {
    PsiFile file = usage.getElement().getContainingFile();
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    Editor editor = FileEditorManager.getInstance(file.getProject()).getSelectedTextEditor();
    LogicalPosition position = editor.offsetToLogicalPosition(startOffset);
    return position + ": '" + StringUtil.first(file.getText().substring(startOffset), 100, true) + "'";
  }

  static void calcRealOffsets(PsiElement startElement, Map<String, RangeMarker> sliceUsageName2Offset,
                               final TIntObjectHashMap<IntArrayList> flownOffsets) {
    fill(sliceUsageName2Offset, "", startElement.getTextOffset(), flownOffsets);
  }

  static Map<String, RangeMarker> extractSliceOffsetsFromDocument(final Document document) {
    Map<String, RangeMarker> sliceUsageName2Offset = new THashMap<String, RangeMarker>();

    extract(document, sliceUsageName2Offset, "");
    assertTrue(!document.getText().contains("<flown"));
    assertTrue(!sliceUsageName2Offset.isEmpty());
    return sliceUsageName2Offset;
  }

  private static void fill(Map<String, RangeMarker> sliceUsageName2Offset, String name, int offset,
                    final TIntObjectHashMap<IntArrayList> flownOffsets) {
    for (int i=1;i<9;i++) {
      String newName = name + i;
      RangeMarker marker = sliceUsageName2Offset.get(newName);
      if (marker == null) break;
      IntArrayList offsets = flownOffsets.get(offset);
      if (offsets == null) {
        offsets = new IntArrayList();
        flownOffsets.put(offset, offsets);
      }
      int newStartOffset = marker.getStartOffset();
      offsets.add(newStartOffset);
      fill(sliceUsageName2Offset, newName, newStartOffset, flownOffsets);
    }
  }

  private static void extract(final Document document, final Map<String, RangeMarker> sliceUsageName2Offset, final String name) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (int i = 1; i < 9; i++) {
          String newName = name + i;
          String s = "<flown" + newName + ">";
          if (!document.getText().contains(s)) break;
          int off = document.getText().indexOf(s);

          document.deleteString(off, off + s.length());
          RangeMarker prev = sliceUsageName2Offset.put(newName, document.createRangeMarker(off, off));
          assertNull(prev);

          extract(document, sliceUsageName2Offset, newName);
        }
      }
    });
  }

  public void testSimple() throws Exception { dotest();}
  public void testLocalVar() throws Exception { dotest();}
  public void testInterMethod() throws Exception { dotest();}
  public void testConditional() throws Exception { dotest();}
  public void testConditional2() throws Exception { dotest();}
  public void testMethodReturn() throws Exception { dotest();}
  public void testVarUse() throws Exception { dotest();}
  public void testWeirdCaretPosition() throws Exception { dotest();}
  public void testAnonClass() throws Exception { dotest();}
  public void testPostfix() throws Exception { dotest();}
  public void testMethodCall() throws Exception { dotest();}
  public void testEnumConst() throws Exception { dotest();}
  public void testTypeAware() throws Exception { dotest();}
  public void testTypeAware2() throws Exception { dotest();}
  public void testViaParameterizedMethods() throws Exception { dotest();}
  public void testTypeErased() throws Exception { dotest();}
  public void testComplexTypeErasure() throws Exception { dotest();}
  public void testGenericsSubst() throws Exception { dotest();}
  public void testOverrides() throws Exception { dotest();}
  public void testGenericBoxing() throws Exception { dotest();}
  public void testAssignment() throws Exception { dotest();}
  public void testGenericImplement() throws Exception { dotest();}
  public void testGenericImplement2() throws Exception { dotest();}
  public void testOverloadConstructor() throws Exception { dotest();}
}
