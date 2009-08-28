/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;

public interface ErrorOptionsProvider extends UnnamedConfigurable {
  ExtensionPointName<ErrorOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.errorOptionsProvider");
}