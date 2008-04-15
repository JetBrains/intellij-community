/**
 * @author Alexey
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HighlightInfoHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder");

  private final PsiFile myContextFile;
  private final HighlightInfoFilter[] myFilters;
  private int myErrorCount;
  private int myWarningCount;
  private int myInfoCount;
  private boolean writable = false;
  private final List<HighlightInfo> myInfos = new ArrayList<HighlightInfo>(5);

  public HighlightInfoHolder(@NotNull PsiFile contextFile, @NotNull HighlightInfoFilter... filters) {
    myContextFile = contextFile;
    myFilters = filters;
  }

  public boolean add(HighlightInfo info) {
    if (!writable) throw new IllegalStateException("Update highlight holder after visit finished; "+this+"; info="+info);
    if (info == null || !accepted(info)) return false;

    HighlightSeverity severity = info.getSeverity();
    if (severity == HighlightSeverity.ERROR) {
      myErrorCount++;
    }
    else if (severity == HighlightSeverity.WARNING) {
      myWarningCount++;
    }
    else if (severity == HighlightSeverity.INFORMATION) {
      myInfoCount++;
    }

    return myInfos.add(info);
  }

  private boolean accepted(HighlightInfo info) {
    for (HighlightInfoFilter filter : myFilters) {
      if (!filter.accept(info, myContextFile)) return false;
    }
    return true;
  }

  public void clear() {
    if (!writable) throw new IllegalStateException("Clearing holder after visit finished; " + this);

    myErrorCount = 0;
    myWarningCount = 0;
    myInfoCount = 0;
    myInfos.clear();
  }

  public boolean hasErrorResults() {
    return myErrorCount != 0;
  }

  public boolean hasInfoResults() {
    return myInfoCount != 0;
  }

  public boolean hasWarningResults() {
    return myWarningCount != 0;
  }

  public int getErrorCount() {
    return myErrorCount;
  }

  public boolean addAll(Collection<? extends HighlightInfo> highlightInfos) {
    if (highlightInfos == null) return false;
    LOG.assertTrue(highlightInfos != this);
    boolean added = false;
    for (final HighlightInfo highlightInfo : highlightInfos) {
      added |= add(highlightInfo);
    }
    return added;
  }

  // ASSERTIONS ONLY
  public void setWritable(final boolean writable) {
    if (this.writable == writable) {
      LOG.error("this.writable != writable");
    }
    this.writable = writable;
  }

  public boolean isWritable() {
    return writable;
  }

  public int size() {
    return myInfos.size();
  }

  public HighlightInfo get(final int i) {
    return myInfos.get(i);
  }
}