package com.intellij.openapi.options;



/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 9, 2003
 * Time: 3:21:22 PM
 * To change this template use Options | File Templates.
 */
public interface ConfigurableGroup {
  String getDisplayName();

  String getShortName();

  Configurable[] getConfigurables();
}
