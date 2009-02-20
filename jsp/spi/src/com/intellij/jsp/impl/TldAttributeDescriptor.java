package com.intellij.jsp.impl;

import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 26, 2007
 * Time: 7:03:23 PM
 * To change this template use File | Settings | File Templates.
 */
public interface TldAttributeDescriptor extends XmlAttributeDescriptor {

  /**
   * Indicates that attribute is a fragment
   * @return
   */
  boolean isIndirectSyntax();

  /**
   * Returns method signature for deferred methods
   * @return
   */
  @Nullable
  String getMethodSignature();

  /**
   * Returns value of "type" subtag
   * @return
   */
  @Nullable
  String getType();
}
