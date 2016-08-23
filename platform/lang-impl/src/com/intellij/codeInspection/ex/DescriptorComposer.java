/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class DescriptorComposer extends HTMLComposerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.DescriptorComposer");
  private final InspectionToolPresentation myTool;

  public DescriptorComposer(@NotNull InspectionToolPresentation tool) {
    myTool = tool;
  }

  @Override
  public void compose(StringBuffer buf, RefEntity refEntity) {
    genPageHeader(buf, refEntity);
    if (myTool.getDescriptions(refEntity) != null) {
      appendHeading(buf, InspectionsBundle.message("inspection.problem.synopsis"));
      buf.append("<div class=\"problem-description\">");
      CommonProblemDescriptor[] descriptions = myTool.getDescriptions(refEntity);

      LOG.assertTrue(descriptions != null);

      startList(buf);
      for (int i = 0; i < descriptions.length; i++) {
        final CommonProblemDescriptor description = descriptions[i];

        startListItem(buf);
        composeDescription(description, i, buf, refEntity);
        doneListItem(buf);
      }

      doneList(buf);
      buf.append("</div>");

      appendResolution(buf,refEntity, quickFixTexts(refEntity, myTool));
    }
    else {
      appendNoProblems(buf);
    }
  }

  public static String[] quickFixTexts(RefEntity where, @NotNull InspectionToolPresentation toolPresentation){
    QuickFixAction[] quickFixes = toolPresentation.getQuickFixes(new RefEntity[] {where}, null);
    if (quickFixes == null) {
      return null;
    }
    List<String> texts = new ArrayList<>();
    for (QuickFixAction quickFix : quickFixes) {
      String text = quickFix.getText();
      if (text == null) continue;
      texts.add(escapeQuickFixText(text));
    }
    return ArrayUtil.toStringArray(texts);
  }

  private static String escapeQuickFixText(String text) {
    return XmlStringUtil.isWrappedInHtml(text) ? XmlStringUtil.stripHtml(text) : StringUtil.escapeXml(text);
  }

  protected void composeAdditionalDescription(@NotNull StringBuffer buf, @NotNull RefEntity refEntity) {}

  @Override
  public void compose(StringBuffer buf, RefEntity refElement, CommonProblemDescriptor descriptor) {
    CommonProblemDescriptor[] descriptions = myTool.getDescriptions(refElement);

    int problemIdx = 0;
    if (descriptions != null) { //server-side inspections
      problemIdx = -1;
      for (int i = 0; i < descriptions.length; i++) {
        CommonProblemDescriptor description = descriptions[i];
        if (description == descriptor) {
          problemIdx = i;
          break;
        }
      }
      if (problemIdx == -1) return;
    }

    genPageHeader(buf, refElement);
    appendHeading(buf, InspectionsBundle.message("inspection.problem.synopsis"));
    //noinspection HardCodedStringLiteral
    buf.append("<br>");
    appendAfterHeaderIndention(buf);

    composeDescription(descriptor, problemIdx, buf, refElement);

    if (refElement instanceof RefElement && !refElement.isValid()) return;

    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > 0) {
      //noinspection HardCodedStringLiteral
      buf.append("<br><br>");
      appendHeading(buf, InspectionsBundle.message("inspection.problem.resolution"));
      //noinspection HardCodedStringLiteral
      buf.append("<br>");
      appendAfterHeaderIndention(buf);

      int idx = 0;
      for (QuickFix fix : fixes) {
        //noinspection HardCodedStringLiteral
        //noinspection HardCodedStringLiteral
        buf.append("<a HREF=\"file://bred.txt#invokelocal:" + (idx++));
        buf.append("\">");
        buf.append(escapeQuickFixText(fix.getName()));
        //noinspection HardCodedStringLiteral
        buf.append("</a>");
        //noinspection HardCodedStringLiteral
        buf.append("<br>");
        appendAfterHeaderIndention(buf);
      }
    }
  }

  protected void composeDescription(@NotNull CommonProblemDescriptor description, int i, @NotNull StringBuffer buf, @NotNull RefEntity refElement) {
    PsiElement expression = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getPsiElement() : null;
    StringBuilder anchor = new StringBuilder();
    VirtualFile vFile = null;

    if (expression != null) {
      vFile = expression.getContainingFile().getVirtualFile();
      if (vFile instanceof VirtualFileWindow) vFile = ((VirtualFileWindow)vFile).getDelegate();

      //noinspection HardCodedStringLiteral
      anchor.append("<a HREF=\"");
      if (myExporter == null){
        //noinspection HardCodedStringLiteral
        anchor.append(appendURL(vFile, "descr:" + i));
      }
      else {
        anchor.append(myExporter.getURL(refElement));
      }

      anchor.append("\">");
      anchor.append(ProblemDescriptorUtil.extractHighlightedText(description, expression).replaceAll("\\$", "\\\\\\$"));
      //noinspection HardCodedStringLiteral
      anchor.append("</a>");
    }
    else {
      //noinspection HardCodedStringLiteral
      anchor.append("<font style=\"font-weight:bold; color:#FF0000\";>");
      anchor.append(InspectionsBundle.message("inspection.export.results.invalidated.item"));
      //noinspection HardCodedStringLiteral
      anchor.append("</font>");
    }

    String descriptionTemplate = description.getDescriptionTemplate();
    if (XmlStringUtil.isWrappedInHtml(descriptionTemplate)) {
      descriptionTemplate = XmlStringUtil.stripHtml(descriptionTemplate);
    }
    else {
      descriptionTemplate = StringUtil.replace(descriptionTemplate, "<code>", "'");
      descriptionTemplate = StringUtil.replace(descriptionTemplate, "</code>", "'");
      descriptionTemplate = XmlStringUtil.escapeString(descriptionTemplate);
    }
    //noinspection HardCodedStringLiteral
    final String reference = "#ref";
    final boolean containsReference = descriptionTemplate.contains(reference);
    String res = descriptionTemplate.replaceAll(reference, anchor.toString());
    final int lineNumber = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getLineNumber() : -1;
    StringBuffer lineAnchor = new StringBuffer();
    if (expression != null && lineNumber > 0) {
      Document doc = FileDocumentManager.getInstance().getDocument(vFile);
      lineAnchor.append(InspectionsBundle.message("inspection.export.results.at.line")).append(" ");
      if (myExporter == null) {
        //noinspection HardCodedStringLiteral
        lineAnchor.append("<a HREF=\"");
        int offset = doc.getLineStartOffset(lineNumber - 1);
        offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), offset, " \t");
        lineAnchor.append(appendURL(vFile, String.valueOf(offset)));
        lineAnchor.append("\">");
      }
      lineAnchor.append(Integer.toString(lineNumber));
      //noinspection HardCodedStringLiteral
      lineAnchor.append("</a>");
      //noinspection HardCodedStringLiteral
      final String location = "#loc";
      if (!res.contains(location)) {
        res += " (" + location + ")";
      }
      res = res.replaceAll(location, lineAnchor.toString());
    }
    buf.append(res.replace("#end", "").replace("#treeend",""));
    buf.append(BR).append(BR);
    composeAdditionalDescription(buf, refElement);
  }

  private static String appendURL(VirtualFile vFile, String anchor) {
    return vFile.getUrl() + "#" + anchor;
  }
}
