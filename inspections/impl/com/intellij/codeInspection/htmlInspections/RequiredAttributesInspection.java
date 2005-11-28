/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.jsp.impl.JspElementDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * User: anna
 * Date: 18-Nov-2005
 */
public class RequiredAttributesInspection extends BaseLocalInspectionTool {

  public String myAdditionalRequiredHtmlAttributes = "";

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection");
  @NonNls public static final String SHORT_NAME = "RequiredAttributes";

  public String getGroupDisplayName() {
    return "";
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.required.attributes.display.name");
  }

  @NonNls
  public String getShortName() {
    return RequiredAttributesInspection.SHORT_NAME;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    final java.util.List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    file.accept(new PsiElementVisitor() {

      public void visitElement(PsiElement element) {
        element.acceptChildren(this);
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      public void visitXmlTag(XmlTag tag) {
        if (tag.getName() == null) {
          return;
        }

        checkTagByDescriptor(tag, manager, problems);

        super.visitXmlTag(tag);
      }
    });
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private void reportOneTagProblem(final XmlTag tag,
                                   final String name,
                                   final String localizedMessage,
                                   final String additional,
                                   InspectionManager manager,
                                   java.util.List<ProblemDescriptor> problems) {

    if (tag instanceof HtmlTag) {
      if (RequiredAttributesInspection.isAdditionallyDeclared(additional, name)) return;
      final ASTNode startTag = XmlChildRole.START_TAG_NAME_FINDER.findChild((ASTNode)tag);
      final PsiElement navigationElement = startTag != null ? startTag.getPsi() : null;
      problems.add(manager.createProblemDescriptor(navigationElement != null ? navigationElement : tag, localizedMessage,
                                                   (LocalQuickFix[])null,
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
    }
  }


  private void checkTagByDescriptor(final XmlTag tag, final InspectionManager manager, final java.util.List<ProblemDescriptor> problems) {
    XmlElementDescriptor elementDescriptor = null;

    final PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      XmlTag parentTag = (XmlTag)parent;
      final XmlElementDescriptor parentDescriptor = parentTag.getDescriptor();

      if (parentDescriptor != null) {
        elementDescriptor = parentDescriptor.getElementDescriptor(tag);
      }

      if (elementDescriptor instanceof AnyXmlElementDescriptor || parentDescriptor == null) {
        elementDescriptor = tag.getDescriptor();
      }
    }
    else {
      //root tag
      elementDescriptor = tag.getDescriptor();
    }

    if (elementDescriptor == null) return;

    XmlAttributeDescriptor[] attributeDescriptors = elementDescriptor.getAttributesDescriptors();
    Set<String> requiredAttributes = null;

    for (XmlAttributeDescriptor attribute : attributeDescriptors) {
      if (attribute != null && attribute.isRequired()) {
        if (requiredAttributes == null) {
          requiredAttributes = new HashSet<String>();
        }
        requiredAttributes.add(attribute.getName(tag));
      }
    }

    if (requiredAttributes != null) {
      for (final String attrName : requiredAttributes) {
        if (tag.getAttribute(attrName, tag.getNamespace()) == null) {
          if (!(elementDescriptor instanceof JspElementDescriptor) ||
              !((JspElementDescriptor)elementDescriptor).isRequiredAttributeImplicitlyPresent(tag, attrName)
            ) {
            final String localizedMessage = XmlErrorMessages.message("element.doesnt.have.required.attribute", tag.getName(), attrName);
            reportOneTagProblem(tag, attrName, localizedMessage, myAdditionalRequiredHtmlAttributes, manager, problems);
          }
        }
      }
    }
  }

  private static boolean isAdditionallyDeclared(final String additional, final String name) {
    StringTokenizer tokenizer = new StringTokenizer(additional, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (name.equals(tokenizer.nextToken())) {
        return true;
      }
    }

    return false;
  }

  public FieldPanel createAdditionalNotRequiredHtmlAttributesPanel() {
    FieldPanel additionalAttributesPanel = new FieldPanel(null,
                                                          InspectionsBundle.message("inspection.javadoc.html.not.required.dialog.title"),
                                                          null, null);

    additionalAttributesPanel.setPreferredSize(new Dimension(150, additionalAttributesPanel.getPreferredSize().height));
    additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        final Document document = e.getDocument();
        try {
          final String text = document.getText(0, document.getLength());
          if (text != null) {
            myAdditionalRequiredHtmlAttributes = text.trim();
          }
        }
        catch (BadLocationException e1) {
          RequiredAttributesInspection.LOG.error(e1);
        }
      }
    });
    additionalAttributesPanel.setText(myAdditionalRequiredHtmlAttributes);
    return additionalAttributesPanel;
  }

}
