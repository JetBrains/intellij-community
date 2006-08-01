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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;

/**
 * User: anna
 * Date: 18-Nov-2005
 */
public class RequiredAttributesInspection extends UnfairLocalInspectionTool implements XmlEntitiesInspection {

  public String myAdditionalRequiredHtmlAttributes = "";

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection");
  @NonNls public static final String SHORT_NAME = "RequiredAttributes";

  public String getGroupDisplayName() {
    return GroupNames.HTML_INSPECTIONS;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.required.attributes.display.name");
  }

  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    return createAdditionalNotRequiredHtmlAttributesPanel();
  }

  public FieldPanel createAdditionalNotRequiredHtmlAttributesPanel() {
    FieldPanel additionalAttributesPanel = new FieldPanel(InspectionsBundle.message("inspection.javadoc.html.not.required.label.text"),
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

  public IntentionAction getIntentionAction(PsiElement psiElement, String name, int type) {
    return new AddHtmlTagOrAttributeToCustomsIntention(getShortName(), psiElement, name, type);
  }

  public String getAdditionalEntries(int type) {
    return myAdditionalRequiredHtmlAttributes;
  }

  public void setAdditionalEntries(int type, String additionalEntries) {
    myAdditionalRequiredHtmlAttributes = additionalEntries;
  }
}
