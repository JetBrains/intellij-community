package com.intellij.jsp.impl;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.Nullable;

import javax.servlet.jsp.tagext.TagExtraInfo;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 11, 2005
 * Time: 8:57:52 PM
 * To change this template use File | Settings | File Templates.
 */
public interface JspElementDescriptor extends XmlElementDescriptor {
  @Nullable
  TagExtraInfo getExtraInfo(@Nullable Module module);
  boolean isRequiredAttributeImplicitlyPresent(XmlTag tag,String attributeName);
  @Nullable
  XmlTag findVariableWithName(String name);

  void resetClassloaderState();
}
