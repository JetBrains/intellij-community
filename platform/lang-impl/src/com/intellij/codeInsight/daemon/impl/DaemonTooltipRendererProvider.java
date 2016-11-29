/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.actions.ShowErrorDescriptionAction;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipLinkHandlerEP;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.codeInspection.ui.DefaultInspectionToolPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.ErrorStripTooltipRendererProvider;
import com.intellij.openapi.editor.impl.TrafficTooltipRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class DaemonTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
  @NonNls private static final String END_MARKER = "<!-- end marker -->";
  private final Project myProject;

  public DaemonTooltipRendererProvider(final Project project) {
    myProject = project;
  }

  @Override
  public TooltipRenderer calcTooltipRenderer(@NotNull final Collection<RangeHighlighter> highlighters) {
    LineTooltipRenderer bigRenderer = null;
    List<HighlightInfo> infos = new SmartList<>();
    Collection<String> tooltips = new THashSet<>(); //do not show same tooltip twice
    for (RangeHighlighter marker : highlighters) {
      final Object tooltipObject = marker.getErrorStripeTooltip();
      if (tooltipObject == null) continue;
      if (tooltipObject instanceof HighlightInfo) {
        HighlightInfo info = (HighlightInfo)tooltipObject;
        if (info.getToolTip() != null && tooltips.add(info.getToolTip())) {
          infos.add(info);
        }
      }
      else {
        final String text = tooltipObject.toString();
        if (tooltips.add(text)) {
          if (bigRenderer == null) {
            bigRenderer = new MyRenderer(text, new Object[] {highlighters});
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }
    }
    if (!infos.isEmpty()) {
      // show errors first
      ContainerUtil.quickSort(infos, (o1, o2) -> {
        int i = SeverityRegistrar.getSeverityRegistrar(myProject).compare(o2.getSeverity(), o1.getSeverity());
        if (i != 0) return i;
        return o1.getToolTip().compareTo(o2.getToolTip());
      });
      final HighlightInfoComposite composite = new HighlightInfoComposite(infos);
      String toolTip = composite.getToolTip();
      MyRenderer myRenderer = new MyRenderer(toolTip == null ? null : UIUtil.convertSpace2Nbsp(toolTip), new Object[]{highlighters});
      if (bigRenderer == null) {
        bigRenderer = myRenderer;
      }
      else {
        myRenderer.addBelow(bigRenderer.getText());
        bigRenderer = myRenderer;
      }
    }
    return bigRenderer;
  }

  @NotNull
  @Override
  public TooltipRenderer calcTooltipRenderer(@NotNull final String text) {
    return new MyRenderer(text, new Object[] {text});
  }

  @NotNull
  @Override
  public TooltipRenderer calcTooltipRenderer(@NotNull final String text, final int width) {
    return new MyRenderer(text, width, new Object[] {text});
  }

  @NotNull
  @Override
  public TrafficTooltipRenderer createTrafficTooltipRenderer(@NotNull Runnable onHide, @NotNull Editor editor) {
    return new TrafficTooltipRendererImpl(onHide, editor);
  }

  private static class MyRenderer extends LineTooltipRenderer {
    public MyRenderer(final String text, Object[] comparable) {
      super(text, comparable);
    }

    public MyRenderer(final String text, final int width, Object[] comparable) {
      super(text, width, comparable);
    }

    @Override
    protected void onHide(final JComponent contentComponent) {
      ShowErrorDescriptionAction.rememberCurrentWidth(contentComponent.getWidth());
    }

    @Override
    protected boolean dressDescription(@NotNull final Editor editor) {
      final List<String> problems = StringUtil.split(UIUtil.getHtmlBody(new Html(myText).setKeepFont(true)), UIUtil.BORDER_LINE);
      String text = "";
      for (String problem : problems) {
        final String ref = getLinkRef(problem);
        if (ref != null) {
          String description = TooltipLinkHandlerEP.getDescription(ref, editor);
          if (description != null) {
            description = DefaultInspectionToolPresentation.stripUIRefsFromInspectionDescription(UIUtil.getHtmlBody(new Html(description).setKeepFont(true)));
            text += UIUtil.getHtmlBody(new Html(problem).setKeepFont(true)).replace(DaemonBundle.message("inspection.extended.description"),
                                                        DaemonBundle.message("inspection.collapse.description")) +
                    END_MARKER + "<p>" + description + UIUtil.BORDER_LINE;
          }
        }
        else {
          text += UIUtil.getHtmlBody(new Html(problem).setKeepFont(true)) + UIUtil.BORDER_LINE;
        }
      }
      if (!text.isEmpty()) { //otherwise do not change anything
        myText = XmlStringUtil.wrapInHtml(StringUtil.trimEnd(text, UIUtil.BORDER_LINE));
        return true;
      }
      return false;
    }

    @Nullable
    private static String getLinkRef(@NonNls String text) {
      final String linkWithRef = "<a href=\"";
      final int linkStartIdx = text.indexOf(linkWithRef);
      if (linkStartIdx >= 0) {
        final String ref = text.substring(linkStartIdx + linkWithRef.length());
        final int quoteIdx = ref.indexOf('"');
        if (quoteIdx > 0) {
          return ref.substring(0, quoteIdx);
        }
      }
      return null;
    }

    @Override
    protected void stripDescription() {
      final List<String> problems = StringUtil.split(UIUtil.getHtmlBody(new Html(myText).setKeepFont(true)), UIUtil.BORDER_LINE);
      myText = "";
      for (String problem1 : problems) {
        final String problem = StringUtil.split(problem1, END_MARKER).get(0);
        myText += UIUtil.getHtmlBody(new Html(problem).setKeepFont(true)).replace(DaemonBundle.message("inspection.collapse.description"),
                                                      DaemonBundle.message("inspection.extended.description")) + UIUtil.BORDER_LINE;
      }
      myText = XmlStringUtil.wrapInHtml(StringUtil.trimEnd(myText, UIUtil.BORDER_LINE));
    }

    @Override
    protected LineTooltipRenderer createRenderer(final String text, final int width) {
      return new MyRenderer(text, width, getEqualityObjects());
    }
  }
}