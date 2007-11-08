/*
 * User: anna
 * Date: 12-Jul-2007
 */
package com.intellij.ide.plugins;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("idea-version")
public class IdeaVersionBean {
  @Attribute("since-build")
  public String sinceBuild;

  @Attribute("until-build")
  public String untilBuild;

}