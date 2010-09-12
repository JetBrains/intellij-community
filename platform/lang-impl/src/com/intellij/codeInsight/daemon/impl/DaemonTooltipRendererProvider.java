/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.ErrorStripTooltipRendererProvider;
import com.intellij.openapi.editor.impl.TrafficTooltipRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DaemonTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
  private final Project myProject;

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
        bigRenderer = new MyRenderer(UIUtil.convertSpace2Nbsp(composite.toolTip));
      }
      else {
        final LineTooltipRenderer renderer = new MyRenderer(UIUtil.convertSpace2Nbsp(composite.toolTip));
        renderer.addBelow(bigRenderer.getText());
        bigRenderer = renderer;
      }
    }
    return bigRenderer;
  }

  public TooltipRenderer calcTooltipRenderer(@NotNull final String text) {
    return new MyRenderer(text);
  }

  public TooltipRenderer calcTooltipRenderer(@NotNull final String text, final int width) {
    return new MyRenderer(text, width);
  }

  @Override
  public TrafficTooltipRenderer createTrafficTooltipRenderer(Runnable onHide) {
    return new TrafficTooltipRendererImpl(onHide);
  }

  private static class MyRenderer extends LineTooltipRenderer {
    public MyRenderer(final String text) {
      super(text);
    }

    public MyRenderer(final String text, final int width) {
      super(text, width);
    }

    protected String convertTextOnLinkHandled(final String text) {
      return text.
        replace(" " + DaemonBundle.message("inspection.extended.description"), "").
        replace("(" + KeymapUtil.getShortcutsText(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) + ")", "");
    }

    protected void onHide(final JComponent contentComponent) {
      ShowErrorDescriptionAction.rememberCurrentWidth(contentComponent.getWidth());
    }

    protected boolean dressDescription(Editor editor) {
      final String[] problems = getHtmlBody(myText).split(BORDER_LINE);
      String text = "";
      for (String problem : problems) {
        final String descriptionPrefix = getDescriptionPrefix(problem);
        if (descriptionPrefix != null) {
          for (final TooltipLinkHandlerEP handlerEP : Extensions.getExtensions(TooltipLinkHandlerEP.EP_NAME)) {
            final String description = handlerEP.getDescription(descriptionPrefix, editor);
            if (description != null) {
              text += getHtmlBody(problem).replace(DaemonBundle.message("inspection.extended.description"),
                                                     DaemonBundle.message("inspection.collapse.description")) + BORDER_LINE + getHtmlBody(description) + BORDER_LINE;
              break;
            }
          }
        }
      }
      if (text.length() > 0) { //otherwise do not change anything
        myText = "<html><body>" +  StringUtil.trimEnd(text, BORDER_LINE) + "</body></html>";
        return true;
      }
      return false;
    }

    @Nullable
    private static String getDescriptionPrefix(@NonNls String text) {
      final int linkIdx = text.indexOf("<a href=");
      if (linkIdx != -1) {
        final String ref = text.substring(linkIdx + 9);
        final int quatIdx = ref.indexOf('"');
        if (quatIdx > 0) {
          return ref.substring(0, quatIdx);
        }
      }
      return null;
    }

    protected void stripDescription() {
      final String[] problems = getHtmlBody(myText).split(BORDER_LINE);
      myText = "<html><body>";
      for (int i = 0; i < problems.length; i++) {
        final String problem = problems[i];
        if (i % 2 == 0) {
          myText += getHtmlBody(problem).replace(DaemonBundle.message("inspection.collapse.description"),
                                                 DaemonBundle.message("inspection.extended.description")) + BORDER_LINE;
        }
      }
      myText = StringUtil.trimEnd(myText, BORDER_LINE) + "</body></html>";
    }

    protected LineTooltipRenderer createRenderer(final String text, final int width) {
      return new MyRenderer(text, width);
    }
  }
}