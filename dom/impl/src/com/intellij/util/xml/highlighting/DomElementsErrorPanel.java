/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.RefreshStatusRenderer;
import com.intellij.lang.annotation.HighlightSeverity;
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

  private Project myProject;
  private DomElement[] myDomElements;

  private final DomElementsRefreshStatusRenderer myErrorStripeRenderer;

  private final Alarm myAlarm = new Alarm();

  public DomElementsErrorPanel(final DomElement... domElements) {
    assert domElements.length > 0;

    myDomElements = domElements;
    final DomManager domManager = domElements[0].getManager();
    myProject = domManager.getProject();

    setPreferredSize(getDimension());

    myErrorStripeRenderer = new DomElementsRefreshStatusRenderer(domElements[0].getRoot().getFile());

    addUpdateRequest();
    domManager.addDomEventListener(new DomChangeAdapter() {
      protected void elementChanged(DomElement element) {
        updatePanel();
      }
    }, this);
  }

  private boolean areValid() {
    for (final DomElement domElement : myDomElements) {
      if (!domElement.isValid()) return false;
    }
    return true;
  }

  private void updatePanel() {
    myAlarm.cancelAllRequests();

    if (!areValid()) return;

    repaint();
    setToolTipText(myErrorStripeRenderer.getTooltipMessage());

    if (!isHighlightingFinished()) {
      addUpdateRequest();
    }
  }

  private boolean isHighlightingFinished() {
    return !areValid() ||
           DomElementAnnotationsManager.getInstance(myProject).isHighlightingFinished(myDomElements);
  }

  private void addUpdateRequest() {
    myAlarm.addRequest(new Runnable() {
      public void run() {
        if (myProject.isOpen()) {
          updatePanel();
        }
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
  }

  public JComponent getComponent() {
    return this;
  }

  public void commit() {

  }

  public void reset() {
    updatePanel();
  }

  protected static Dimension getDimension() {
    return new Dimension(ERRORS_FOUND_ICON.getIconWidth() + 2, ERRORS_FOUND_ICON.getIconHeight() + 2);
  }

  private class DomElementsRefreshStatusRenderer extends RefreshStatusRenderer {
    public DomElementsRefreshStatusRenderer(final PsiFile xmlFile) {
      super(xmlFile.getProject(), (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(xmlFile.getProject()), PsiDocumentManager.getInstance(xmlFile.getProject()).getDocument(xmlFile), xmlFile);
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

    protected boolean isInspectionCompleted() {
      return isHighlightingFinished();
    }

    protected boolean isErrorAnalyzingFinished() {
      return isHighlightingFinished();
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
