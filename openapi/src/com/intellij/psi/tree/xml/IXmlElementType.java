package com.intellij.psi.tree.xml;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 12:01:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class IXmlElementType extends IElementType{
  public IXmlElementType(String debugName) {
    super(debugName, StdFileTypes.XML.getLanguage());
  }
}
