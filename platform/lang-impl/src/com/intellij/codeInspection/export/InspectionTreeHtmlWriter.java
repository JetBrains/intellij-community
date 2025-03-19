// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.export;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.ui.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author Dmitry Batkovich
 */
@ApiStatus.Internal
public final class InspectionTreeHtmlWriter {
  @SuppressWarnings("SpellCheckingInspection")
  private static final String ERROR_COLOR = "ffabab";
  private static final String WARNING_COLOR = "f2f794";

  private final InspectionTree myTree;
  private final Path myOutputDir;
  private final InspectionProfile myProfile;
  private final RefManager myManager;

  public InspectionTreeHtmlWriter(@NotNull InspectionTree tree,
                                  @NotNull InspectionProfile profile,
                                  @NotNull RefManager refManager,
                                  @NotNull Path outputDir) {
    myTree = tree;
    myProfile = profile;
    myManager = refManager;
    myOutputDir = outputDir;
    serializeTreeToHtml();
  }

  private void serializeTreeToHtml() {
    HTMLExportUtil.writeFile(myOutputDir, "index.html", myTree.getContext().getProject(), w -> {
      String title = myTree.getContext().getView().getViewTitle();
      appendHeader(w, title);
      w.append("<div id=\"inspection-tree\">\n<h4>").append(title).append("</h4>\n");
      InspectionTreeTailRenderer<IOException> tailRenderer = new InspectionTreeTailRenderer<>(myTree.getContext()) {
        @Override
        protected void appendText(String text, SimpleTextAttributes attributes) throws IOException {
          w.append(escapeNonBreakingSymbols(text));
        }

        @Override
        protected void appendText(String text) throws IOException {
          w.append(escapeNonBreakingSymbols(text));
        }
      };
      w.append("\n<ol class=\"tree\">");
      traverseInspectionTree(myTree.getInspectionTreeModel().getRoot(), n -> {
        int nodeId = System.identityHashCode(n);
        w.append("\n<li><label for=\"")
          .append(String.valueOf(nodeId))
          .append("\">")
          .append(convertNodeToHtml(n))
          .append("&nbsp;<span class=\"grayout\">");
        tailRenderer.appendTailText(n);
        w.append("</span></label><input type=\"checkbox\"");
        if (isChecked(n)) {
          w.append(" checked");
        }
        w.append(" onclick=\"navigate(").append(String.valueOf(nodeId)).append(")\"");
        w.append(" id=\"").append(String.valueOf(nodeId)).append("\">");
        if (n instanceof SuppressableInspectionTreeNode) {
          RefEntity e = ((SuppressableInspectionTreeNode)n).getElement();
          if (e != null) {
            w.append("\n<div id=\"d").append(String.valueOf(nodeId)).append("\" style=\"display:none\">");
            StringBuilder buf = new StringBuilder();
            ((SuppressableInspectionTreeNode)n).getPresentation().getComposer().compose(buf, e);
            w.append(buf.toString());
            w.append("</div>");
          }
        }
        w.append("\n<ol class=\"tree\">");
      }, n -> w.append("\n</ol>"));
      w.append("""
                 
                 </ol>
                 </div>
                 <div id="description">
                   <h4>Problem description</h4>
                   <div id="preview">Select a problem element in tree</div>
                 </div>
                 </body>
                 </html>""");
    });

    InspectionTreeHtmlExportResources.copyInspectionReportResources(myOutputDir);
  }

  private static boolean isChecked(InspectionTreeNode node) {
    return node instanceof InspectionRootNode || node.getChildCount() == 1 && isChecked(node.getParent());
  }

  private static void traverseInspectionTree(InspectionTreeNode node,
                                             ThrowableConsumer<? super InspectionTreeNode, ? extends IOException> preAction,
                                             ThrowableConsumer<? super InspectionTreeNode, ? extends IOException> postAction) throws IOException {
    if (node.isExcluded()) {
      return;
    }
    preAction.consume(node);
    for (InspectionTreeNode child : node.getChildren()) {
      traverseInspectionTree(child, preAction, postAction);
    }
    postAction.consume(node);
  }

  private String convertNodeToHtml(InspectionTreeNode node) {
    if (node instanceof InspectionRootNode) {
      return "<b>" + escapeNonBreakingSymbols(node) + "</b>";
    }
    else if (node instanceof ProblemDescriptionNode) {
      final CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)node).getDescriptor();
      String warningLevelName = "";
      String color = null;
      if (descriptor instanceof ProblemDescriptorBase) {
        InspectionToolWrapper<?, ?> tool = ((ProblemDescriptionNode)node).getToolWrapper();
        HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
        HighlightSeverity severity = myProfile.getErrorLevel(key, ((ProblemDescriptorBase)descriptor).getStartElement()).getSeverity();
        HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);
        if (HighlightDisplayLevel.ERROR.equals(level)) {
          color = ERROR_COLOR;
        }
        else if (HighlightDisplayLevel.WARNING.equals(level)) {
          color = WARNING_COLOR;
        }
        warningLevelName = StringUtil.toUpperCase(level.getSeverity().getDisplayName());
      }

      final StringBuilder sb = new StringBuilder();
      sb.append("<span style=\"margin:1px;");
      if (color != null) {
        sb.append("background:#").append(color);
      }
      sb.append("\">").append(warningLevelName).append("</span>&nbsp;").append(escapeNonBreakingSymbols(node));
      return sb.toString();
    }
    else if (node instanceof RefElementNode) {
      String type = myManager.getType(Objects.requireNonNull(((RefElementNode)node).getElement()));
      return type + "&nbsp;<b>" + escapeNonBreakingSymbols(node) + "</b>";
    }
    else if (node instanceof InspectionNode) {
      return node.getClass() != InspectionNode.class
             ? "<b>" + escapeNonBreakingSymbols(node) + "</b>"
             : "<b>" + escapeNonBreakingSymbols(node) + "</b>&nbsp;inspection";
    }
    else if (node instanceof InspectionGroupNode) {
      return "<b>" + escapeNonBreakingSymbols(node) + "</b>&nbsp;group";
    }
    else {
      return escapeNonBreakingSymbols(node);
    }
  }

  private void appendHeader(@NotNull Writer writer, String title) throws IOException {
    writer.append("""
                    <html>
                    <head>
                      <meta http-equiv="Content-Type" content="text/html;charset=utf-8">
                      <meta name="author" content="JetBrains">
                      <script type="text/javascript" src="script.js"></script>
                      <link rel="stylesheet" type="text/css" href="styles.css">
                      <title>""")
      .append(title)
      .append("""
                  </title>
                </head>
                <body>
                """);
  }

  private static String escapeNonBreakingSymbols(@NotNull Object source) {
    return StringUtil.replace(StringUtil.escapeXmlEntities(source.toString()), Arrays.asList(" ", "-"), Arrays.asList("&nbsp;", "&#8209;"));
  }
}
