package com.intellij.jsp.impl;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.openapi.module.Module;

import javax.servlet.jsp.tagext.TagExtraInfo;

import org.jetbrains.annotations.Nullable;

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
}
