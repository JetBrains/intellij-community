package com.intellij.util.xml.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.actions.generate.DomTemplateRunner;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
public class DomTemplateRunnerImpl extends DomTemplateRunner {
  private Project myProject;

  public DomTemplateRunnerImpl(Project project) {
    myProject = project;
  }

  public <T extends DomElement> void runTemplate(final T t, final String mappingId, final Editor editor) {
    final Template template = getTemplate(mappingId);
    runTemplate(t, editor, template);
  }

  public <T extends DomElement> void runTemplate(final T t, final Editor editor, @Nullable final Template template) {
    if (template != null) {
      DomElement copy = t.createStableCopy();
      PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      XmlTag tag = copy.getXmlTag();
      assert tag != null;
      editor.getCaretModel().moveToOffset(tag.getTextRange().getStartOffset());
      copy.undefine();

      PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(editor.getDocument());

      template.setToReformat(true);
      TemplateManager.getInstance(myProject).startTemplate(editor, template);
    }
  }

  @Nullable
  protected Template getTemplate(final String mappingId) {
    return mappingId != null ? TemplateSettings.getInstance().getTemplateById(mappingId) : null;
  }
}