package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.api.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Feb 1, 2006
 * Time: 9:18:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlParameterInfoHandler implements ParameterInfoHandler<XmlTag,XmlElementDescriptor> {
  private static final Comparator<XmlAttributeDescriptor> COMPARATOR = new Comparator<XmlAttributeDescriptor>() {
    public int compare(final XmlAttributeDescriptor o1, final XmlAttributeDescriptor o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };

  public Object[] getParametersForLookup(LookupItem item, ParameterInfoContext context) {
    final Object lookupItem = item.getObject();
    if (lookupItem instanceof XmlElementDescriptor) return new Object[]{lookupItem};
    return null;
  }

  public Object[] getParametersForDocumentation(final XmlElementDescriptor p, final ParameterInfoContext context) {
    return getSortedDescriptors(p);
  }

  private static XmlAttributeDescriptor[] getSortedDescriptors(final XmlElementDescriptor p) {
    final XmlAttributeDescriptor[] xmlAttributeDescriptors = p.getAttributesDescriptors();
    Arrays.sort(xmlAttributeDescriptors, COMPARATOR);
    return xmlAttributeDescriptors;
  }

  public boolean couldShowInLookup() {
    return true;
  }

  public XmlTag findElementForParameterInfo(final CreateParameterInfoContext context) {
    final XmlTag tag = findXmlTag(context.getFile(), context.getOffset());
    final XmlElementDescriptor descriptor = tag != null ? tag.getDescriptor() : null;

    if (descriptor == null) {
      DaemonCodeAnalyzer.getInstance(context.getProject()).updateVisibleHighlighters(context.getEditor());
      return null;
    }

    context.setItemsToShow(new Object[] {descriptor});
    return tag;
  }

  public void showParameterInfo(final @NotNull XmlTag element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  public XmlTag findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    final XmlTag tag = findXmlTag(context.getFile(), context.getOffset());
    if (tag != null) {
      final PsiElement currentXmlTag = context.getParameterOwner();
      if (currentXmlTag == null || currentXmlTag == tag) return tag;
    }

    return null;
  }

  public void updateParameterInfo(final XmlTag o, final UpdateParameterInfoContext context) {
    if (context.getParameterOwner() == null || o.equals(context.getParameterOwner())) {
      context.setParameterOwner( o );
    } else {
      context.removeHint();
    }
  }

  public String getParameterCloseChars() {
    return null;
  }

  public boolean tracksParameterIndex() {
    return false;
  }

  @Nullable
  private static XmlTag findXmlTag(PsiFile file, int offset){
    if (!(file instanceof XmlFile)) return null;

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    element = element.getParent();

    while (element != null) {
      if (element instanceof XmlTag) {
        XmlTag tag = (XmlTag)element;

        final PsiElement[] children = tag.getChildren();

        if (offset <= children[0].getTextRange().getStartOffset()) return null;

        for (PsiElement child : children) {
          final TextRange range = child.getTextRange();
          if (range.getStartOffset() <= offset && range.getEndOffset() > offset) return tag;

          if (child instanceof XmlToken) {
            XmlToken token = (XmlToken)child;
            if (token.getTokenType() == XmlTokenType.XML_TAG_END) return null;
          }
        }

        return null;
      }

      element = element.getParent();
    }

    return null;
  }

  public void updateUI(XmlElementDescriptor o, ParameterInfoUIContext context) {
    updateElementDescriptor(o, context);
  }

  private static void updateElementDescriptor(XmlElementDescriptor descriptor, ParameterInfoUIContext context) {
    final XmlAttributeDescriptor[] attributes = descriptor != null ? getSortedDescriptors(descriptor) : XmlAttributeDescriptor.EMPTY;

    StringBuffer buffer = new StringBuffer();
    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    if (attributes.length == 0) {
      buffer.append(CodeInsightBundle.message("xml.tag.info.no.attributes"));
    }
    else {
      StringBuffer text1 = new StringBuffer(" ");
      StringBuffer text2 = new StringBuffer(" ");
      StringBuffer text3 = new StringBuffer(" ");

      final XmlTag parameterOwner  = (XmlTag)context.getParameterOwner();

      for (XmlAttributeDescriptor attribute : attributes) {
        if (parameterOwner != null && parameterOwner.getAttributeValue(attribute.getName()) != null) {
          if (!(text1.toString().equals(" "))) {
            text1.append(", ");
          }
          text1.append(attribute.getName());
        }
        else if (attribute.isRequired()) {
          if (!(text2.toString().equals(" "))) {
            text2.append(", ");
          }
          text2.append(attribute.getName());
        }
        else {
          if (!(text3.toString().equals(" "))) {
            text3.append(", ");
          }
          text3.append(attribute.getName());
        }
      }

      if (!text1.toString().equals(" ") && !text2.toString().equals(" ")) {
        text1.append(", ");
      }

      if (!text2.toString().equals(" ") && !text3.toString().equals(" ")) {
        text2.append(", ");
      }

      if (!text1.toString().equals(" ") && !text3.toString().equals(" ") && text2.toString().equals(" ")) {
        text1.append(", ");
      }

      buffer.append(text1);
      highlightStartOffset = buffer.length();
      buffer.append(text2);
      highlightEndOffset = buffer.length();
      buffer.append(text3);
    }

    context.setupUIComponentPresentation(buffer.toString(), highlightStartOffset, highlightEndOffset, false,
                                         false, true, context.getDefaultParameterColor());
  }
}
