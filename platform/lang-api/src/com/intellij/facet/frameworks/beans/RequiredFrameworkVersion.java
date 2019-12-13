package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author nik
 */
@Tag("requires")
@ApiStatus.Internal
public class RequiredFrameworkVersion {
  @Attribute("group")
  public String myGroupId;

  @Attribute("version")
  public String myVersion;
}

