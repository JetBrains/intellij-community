package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author maxim.mossienko
 */
public class AddXsiSchemaLocationForExtResourceAction extends BaseExtResourceAction {
  @NonNls private static final String XMLNS_XSI_ATTR_NAME = "xmlns:xsi";
  @NonNls private static final String XSI_SCHEMA_LOCATION_ATTR_NAME = "xsi:schemaLocation";

  protected String getQuickFixKeyId() {
    return "add.xsi.schema.location.for.external.resource";
  }

  protected void doInvoke(@NotNull final PsiFile file, final int offset, @NotNull final String uri, final Editor editor) throws IncorrectOperationException {
    final XmlTag tag = PsiTreeUtil.getParentOfType(file.findElementAt(offset), XmlTag.class);
    if (tag == null) return;
    final List<String> schemaLocations = new ArrayList<String>();

    CreateNSDeclarationIntentionFix.processExternalUris(new CreateNSDeclarationIntentionFix.TagMetaHandler(tag.getLocalName()), file, new CreateNSDeclarationIntentionFix.ExternalUriProcessor() {
      public void process(@NotNull final String currentUri, final String url) {
        if (currentUri.equals(uri) && url != null) schemaLocations.add(url);
      }

    });

    CreateNSDeclarationIntentionFix.runActionOverSeveralAttributeValuesAfterLettingUserSelectTheNeededOne(
      schemaLocations.toArray(new String[schemaLocations.size()]),
      file.getProject(),
      new CreateNSDeclarationIntentionFix.StringToAttributeProcessor() {
        public void doSomethingWithGivenStringToProduceXmlAttributeNowPlease(@NotNull final String attrName)
        throws IncorrectOperationException {
          doIt(file, editor, uri, tag, attrName);
        }
      }, XmlErrorMessages.message("select.namespace.location.title"), this, editor);
  }

  private static void doIt(final PsiFile file, final Editor editor, final String uri, final XmlTag tag, final String s) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();

    if (tag.getAttributeValue(XMLNS_XSI_ATTR_NAME) == null) {
      tag.add(elementFactory.createXmlAttribute(XMLNS_XSI_ATTR_NAME, XmlUtil.XML_SCHEMA_INSTANCE_URI));
    }

    final XmlAttribute locationAttribute = tag.getAttribute(XSI_SCHEMA_LOCATION_ATTR_NAME);
    final String toInsert = uri + " " + s;
    int offset = s.length();

    if (locationAttribute == null) {
      tag.add(elementFactory.createXmlAttribute(XSI_SCHEMA_LOCATION_ATTR_NAME, toInsert));
    } else {
      final String newValue = locationAttribute.getValue() + "\n" + toInsert;
      locationAttribute.setValue(newValue);
    }

    CodeStyleManager.getInstance(file.getProject()).reformat(tag);

    final TextRange range = tag.getAttribute(XSI_SCHEMA_LOCATION_ATTR_NAME).getValueElement().getTextRange();
    final TextRange textRange = new TextRange(range.getEndOffset() - offset - 1, range.getEndOffset() - 1);
    editor.getCaretModel().moveToOffset(textRange.getStartOffset());
  }
}