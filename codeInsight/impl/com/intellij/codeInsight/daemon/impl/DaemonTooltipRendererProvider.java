/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.actions.ShowErrorDescriptionAction;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.openapi.editor.ex.ErrorStripTooltipRendererProvider;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DaemonTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
  private Project myProject;

  public DaemonTooltipRendererProvider(final Project project) {
    myProject = project;
  }

  public TooltipRenderer calcTooltipRenderer(@NotNull final Collection<RangeHighlighter> highlighters) {
    LineTooltipRenderer bigRenderer = null;
    List<HighlightInfo> infos = new SmartList<HighlightInfo>();
    Collection<String> tooltips = new THashSet<String>(); //do not show same tooltip twice
    for (RangeHighlighter marker : highlighters) {
      final Object tooltipObject = marker.getErrorStripeTooltip();
      if (tooltipObject == null) continue;
      if (tooltipObject instanceof HighlightInfo) {
        HighlightInfo info = (HighlightInfo)tooltipObject;
        if (info.toolTip != null && tooltips.add(info.toolTip)) {
          infos.add(info);
        }
      }
      else {
        final String text = tooltipObject.toString();
        if (tooltips.add(text)) {
          if (bigRenderer == null) {
            bigRenderer = new MyRenderer(text);
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }
    }
    if (!infos.isEmpty()) {
      // show errors first
      Collections.sort(infos, new Comparator<HighlightInfo>() {
        public int compare(final HighlightInfo o1, final HighlightInfo o2) {
          int i = SeverityRegistrar.getInstance(myProject).compare(o2.getSeverity(), o1.getSeverity());
          if (i != 0) return i;
          return o1.toolTip.compareTo(o2.toolTip);
        }
      });
      final HighlightInfoComposite composite = new HighlightInfoComposite(infos);
      if (bigRenderer == null) {
        bigRenderer = new MyRenderer(composite.toolTip);
      }
      else {
        final LineTooltipRenderer renderer = new MyRenderer(composite.toolTip);
        renderer.addBelow(bigRenderer.getText());
        bigRenderer = renderer;
      }
    }
    return bigRenderer;
  }

  private static class MyRenderer extends LineTooltipRenderer {
    public MyRenderer(final String text) {
      super(text);
    }

    protected void onHide(final JComponent contentComponent) {
      ShowErrorDescriptionAction.rememberCurrentWidth(contentComponent.getWidth());
    }
  }
}