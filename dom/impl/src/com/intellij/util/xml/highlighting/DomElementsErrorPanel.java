/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.RefreshStatusRenderer;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.xml.DomChangeAdapter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ui.CommittablePanel;

import javax.swing.*;
import java.awt.*;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsErrorPanel extends JPanel implements CommittablePanel {
  private static final Icon ERRORS_FOUND_ICON = IconLoader.getIcon("/general/errorsInProgress.png");

  private static final int ALARM_PERIOD = 241;

  private DomElement[] myDomElements;
  private DomManager myDomManager;

  private final DomChangeAdapter myDomChangeListener;
  private final DomElementsRefreshStatusRenderer myErrorStripeRenderer;

  private final Alarm myAlarm = new Alarm();

  public DomElementsErrorPanel(final DomElement domElement) {
    this(new DomElement[]{domElement}, domElement.getManager(), domElement.getRoot().getFile());
  }

  public DomElementsErrorPanel(final DomElement[] domElements, DomManager domManager, PsiFile file) {
    myDomElements = domElements;
    myDomManager = domManager;

    final Document document = PsiDocumentManager.getInstance(myDomManager.getProject()).getDocument(file);

    setPreferredSize(getDimension());

    myErrorStripeRenderer = new DomElementsRefreshStatusRenderer(myDomManager.getProject(), document, file);

    addUpdateRequest();

    myDomChangeListener = new DomChangeAdapter() {
      protected void elementChanged(DomElement element) {
        updatePanel();
      }
    };

    myDomManager.addDomEventListener(myDomChangeListener);
  }

  private void updatePanel() {
    myAlarm.cancelAllRequests();

    repaint();
    setToolTipText(myErrorStripeRenderer.getTooltipMessage());

    if (!myErrorStripeRenderer.getDaemonCodeAnalyzerStatus().inspectionFinished) {
      addUpdateRequest();
    }
  }

  private void addUpdateRequest() {
    myAlarm.addRequest(new Runnable() {
      public void run() {
        updatePanel();
      }
    }, ALARM_PERIOD);
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    myErrorStripeRenderer.paint(this, g, new Rectangle(0, 0, getWidth(), getHeight()));
  }

  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  public void dispose() {
    myAlarm.cancelAllRequests();
    myDomManager.removeDomEventListener(myDomChangeListener);
  }

  public JComponent getComponent() {
    return this;
  }

  public void commit() {

  }

  public void reset() {

  }

  protected static Dimension getDimension() {
    return new Dimension(ERRORS_FOUND_ICON.getIconWidth() + 2, ERRORS_FOUND_ICON.getIconHeight() + 2);
  }

  private class DomElementsRefreshStatusRenderer extends RefreshStatusRenderer {
    public DomElementsRefreshStatusRenderer(final Project project, final Document document, final PsiFile xmlFile) {
      super(project, (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project), document, xmlFile);
    }

    protected int getErrorsCount(final HighlightSeverity minSeverity) {
      int sum = 0;
      for (DomElement element : myDomElements) {
        final Project project = getProject();
        final DomElementsProblemsHolder holder = DomElementAnnotationsManager.getInstance(project).getCachedProblemHolder(element);
        if (minSeverity.equals(HighlightSeverity.WARNING)) {
          sum += holder.getProblems(element, true, true).size();
        } else {
          sum += holder.getProblems(element, true, true, minSeverity).size();
        }
      }
      return sum;
    }

    public DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus() {
      return super.getDaemonCodeAnalyzerStatus();
    }
  }

  // private static class MyRefreshStatusRenderer extends RefreshStatusRenderer {
  //  public MyRefreshStatusRenderer(final Project project, final Document document, final XmlFile xmlFile) {
  //    super(project, (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project), document, xmlFile);
  //  }
  //
  //  public DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus() {
  //    return super.getDaemonCodeAnalyzerStatus();
  //  }
  //}
}
