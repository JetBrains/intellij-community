/**
 * @author Alexey
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HighlightInfoHolder extends ArrayList<HighlightInfo> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder");

  private PsiFile myContextFile;
  private HighlightInfoFilter[] myFilters;
  private int myErrorCount;
  private int myWarningCount;
  private int myInfoCount;
  private boolean writable = false;

  public HighlightInfoHolder(PsiFile contextFile, HighlightInfoFilter[] filters) {
    super(5);
    myContextFile = contextFile;
    myFilters = filters;
  }

  public boolean add(HighlightInfo info) {
    if (!writable) throw new UnsupportedOperationException("Update highlight holder after visit finished; "+this+"; info="+info);
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

    return super.add(info);
  }

  private boolean accepted(HighlightInfo info) {
    for (HighlightInfoFilter filter : myFilters) {
      if (!filter.accept(info, myContextFile)) return false;
    }
    return true;
  }

  public void clear() {
    if (!writable) throw new UnsupportedOperationException("Clearing holder after visit finished; " + this);

    myErrorCount = 0;
    myWarningCount = 0;
    myInfoCount = 0;
    super.clear();
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

  public HighlightInfo set(final int index, final HighlightInfo element) {
    throw new UnsupportedOperationException();
  }

  public void add(final int index, final HighlightInfo element) {
    throw new UnsupportedOperationException();
  }

  public HighlightInfo remove(final int index) {
    throw new UnsupportedOperationException();
  }

  public boolean remove(final Object o) {
    throw new UnsupportedOperationException();
  }

  public boolean addAll(final int index, final Collection<? extends HighlightInfo> c) {
    throw new UnsupportedOperationException();
  }

  protected void removeRange(final int fromIndex, final int toIndex) {
    throw new UnsupportedOperationException();
  }

  public boolean removeAll(final Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public boolean retainAll(final Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public List<HighlightInfo> subList(final int fromIndex, final int toIndex) {
    throw new UnsupportedOperationException();
  }
}