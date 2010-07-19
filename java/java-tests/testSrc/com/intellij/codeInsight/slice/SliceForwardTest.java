package com.intellij.codeInsight.slice;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SliceForwardHandler;
import com.intellij.slicer.SliceManager;
import com.intellij.slicer.SliceUsage;
import com.intellij.util.containers.IntArrayList;
import gnu.trove.TIntObjectHashMap;

import java.util.Collection;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceForwardTest extends DaemonAnalyzerTestCase {
  private final TIntObjectHashMap<IntArrayList> myFlownOffsets = new TIntObjectHashMap<IntArrayList>();

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }
  
  private void dotest() throws Exception {
    configureByFile("/codeInsight/slice/forward/"+getTestName(false)+".java");
    Map<String, RangeMarker> sliceUsageName2Offset = SliceBackwardTest.extractSliceOffsetsFromDocument(getEditor().getDocument());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = new SliceForwardHandler().getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    SliceBackwardTest.calcRealOffsets(element, sliceUsageName2Offset, myFlownOffsets);
    Collection<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = false;
    SliceUsage usage = SliceManager.createRootUsage(element, params);
    SliceBackwardTest.checkUsages(usage, false, myFlownOffsets);
  }

  public void testSimple() throws Exception { dotest();}
  public void testInterMethod() throws Exception { dotest();}
  public void testParameters() throws Exception { dotest();}
}