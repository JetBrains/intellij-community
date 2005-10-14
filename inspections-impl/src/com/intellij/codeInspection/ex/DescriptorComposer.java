package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.text.CharArrayUtil;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author max
 */
public class DescriptorComposer extends HTMLComposer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.DescriptorComposer");
  private DescriptorProviderInspection myTool;

  public DescriptorComposer(DescriptorProviderInspection tool) {
    myTool = tool;
  }

  public void compose(StringBuffer buf, RefEntity refEntity) {
    if (refEntity instanceof RefElement) {
      RefElement refElement = (RefElement)refEntity;

      genPageHeader(buf, refElement);

      if (myTool.getDescriptions(refElement) != null) {
        appendHeading(buf, InspectionsBundle.message("inspection.problem.synopsis"));

        ProblemDescriptor[] descriptions = myTool.getDescriptions(refElement);

        startList();
        for (int i = 0; i < descriptions.length; i++) {
          final ProblemDescriptor description = descriptions[i];

          startListItem(buf);
          composeDescription(description, i, buf);
          doneListItem(buf);
        }

        doneList(buf);

        appendResolution(buf, myTool, refElement);
      }
      else {
        appendNoProblems(buf);
      }
    }
  }

  public void compose(StringBuffer buf, RefElement refElement, ProblemDescriptor descriptor) {
    ProblemDescriptor[] descriptions = myTool.getDescriptions(refElement);

    int problemIdx = -1;
    for (int i = 0; i < descriptions.length; i++) {
      ProblemDescriptor description = descriptions[i];
      if (description == descriptor) {
        problemIdx = i;
        break;
      }
    }
    if (problemIdx == -1) return;

    genPageHeader(buf, refElement);
    appendHeading(buf, InspectionsBundle.message("inspection.problem.synopsis"));
    //noinspection HardCodedStringLiteral
    buf.append("<br>");
    appendAfterHeaderIndention(buf);

    composeDescription(descriptor, problemIdx, buf);
    final LocalQuickFix[] fixes = descriptor.getFixes();
    if (fixes != null) {
      //noinspection HardCodedStringLiteral
      buf.append("<br><br>");
      appendHeading(buf, InspectionsBundle.message("inspection.problem.resolution"));
      //noinspection HardCodedStringLiteral
      buf.append("<br>");
      appendAfterHeaderIndention(buf);

      int idx = 0;
      for (LocalQuickFix fix : fixes) {
        //noinspection HardCodedStringLiteral
        buf.append("<font style=\"font-family:verdana;\"");
        //noinspection HardCodedStringLiteral
        buf.append("<a HREF=\"file://bred.txt#invokelocal:" + (idx++));
        buf.append("\">");
        buf.append(fix.getName());
        //noinspection HardCodedStringLiteral
        buf.append("</a></font>");
        //noinspection HardCodedStringLiteral
        buf.append("<br>");
        appendAfterHeaderIndention(buf);
      }
    }
  }

  protected void composeDescription(final ProblemDescriptor description, int i, StringBuffer buf) {
    PsiElement expression = description.getPsiElement();
    StringBuffer anchor = new StringBuffer();
    if (expression != null) {
      VirtualFile vFile = expression.getContainingFile().getVirtualFile();

      //noinspection HardCodedStringLiteral
      anchor.append("<a HREF=\"");
      try {
        //noinspection HardCodedStringLiteral
        anchor.append(new URL(vFile.getUrl() + "#descr:" + i));
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }

      anchor.append("\">");
      anchor.append(expression.getText().replaceAll("\\$", "\\\\\\$"));
      //noinspection HardCodedStringLiteral
      anchor.append("</a>");
    }
    else {
      //noinspection HardCodedStringLiteral
      anchor.append("<font style=\"font-family:verdana; font-weight:bold; color:#FF0000\";>");
      anchor.append(InspectionsBundle.message("inspection.export.results.invalidated.item"));
      //noinspection HardCodedStringLiteral
      anchor.append("</font>");
    }

    String descriptionTemplate = description.getDescriptionTemplate();
    if (descriptionTemplate != null) {
      //noinspection HardCodedStringLiteral
      String res = descriptionTemplate.replaceAll("#ref", anchor.toString());
      final int lineNumber = description.getLineNumber();
      StringBuffer lineAnchor = new StringBuffer();
      if (expression != null && lineNumber > 0) {
        VirtualFile vFile = expression.getContainingFile().getVirtualFile();
        Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        lineAnchor.append(InspectionsBundle.message("inspection.export.results.at.line") + " ");
        //noinspection HardCodedStringLiteral
        lineAnchor.append("<a HREF=\"");
        try {
          int offset = doc.getLineStartOffset(lineNumber - 1);
          offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), offset, " \t");
          lineAnchor.append(new URL(vFile.getUrl() + "#" + offset));
        }
        catch (MalformedURLException e) {
          LOG.error(e);
        }
        lineAnchor.append("\">");
        lineAnchor.append(Integer.toString(lineNumber));
        //noinspection HardCodedStringLiteral
        lineAnchor.append("</a>");
        //noinspection HardCodedStringLiteral
        res = res.replaceAll("#loc", lineAnchor.toString());
      }
      buf.append(res);
    }
    else {
      buf.append(InspectionsBundle.message("inspection.export.results.no.error"));
    }
  }
}
