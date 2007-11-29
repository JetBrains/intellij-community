package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class AddXsiSchemaLocationForExtResourceAction extends BaseExtResourceAction {

  protected String getQuickFixKeyId() {
    return "add.xsi.schema.location.for.external.resource";
  }

  protected void doInvoke(final PsiFile file, final int offset, final String uri) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(file.findElementAt(offset), XmlTag.class);
    if (tag == null) return;
    final List<String> schemaLocations = new ArrayList<String>();

    //XmlHighlightVisitor.processExternalUris(tag, file, new XmlHighlightVisitor.ExternalUriProcessor() {
    //  public void process(@NotNull final String currentUri, final String url) {
    //    if (currentUri.equals(uri) && url != null) schemaLocations.add(url);
    //  }
    //
    //  public boolean acceptXmlNs() {
    //    return true;
    //  }
    //
    //  public boolean acceptTaglib() {
    //    return false;
    //  }
    //});
  }
}