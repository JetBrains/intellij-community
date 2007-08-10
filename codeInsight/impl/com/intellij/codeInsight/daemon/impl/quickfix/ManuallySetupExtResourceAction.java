package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.j2ee.extResources.ExternalResourceConfigurable;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class ManuallySetupExtResourceAction extends BaseIntentionAction {

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    String uri = FetchExtResourceAction.findUri(file, offset);

    if (uri == null) return false;

    XmlFile xmlFile = XmlUtil.findXmlFile(file, uri);
    if (xmlFile != null) return false;

    setText(QuickFixBundle.message("manually.setup.external.resource"));
    return true;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("manually.setup.external.resource");
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final String uri = FetchExtResourceAction.findUri(file, offset);
    if (uri == null) return;

    ExternalResourceManager.getInstance().addResource(uri,"");
    final ExternalResourceConfigurable component = ShowSettingsUtil.getInstance().findApplicationConfigurable(ExternalResourceConfigurable.class);
    ShowSettingsUtil.getInstance().editConfigurable(
      project,
      component,
      new Runnable() {
        public void run() {
          component.selectResource(uri);
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }
}
