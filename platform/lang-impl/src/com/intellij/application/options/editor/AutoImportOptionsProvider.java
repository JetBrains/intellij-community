package com.intellij.application.options.editor;

import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author Dmitry Avdeev
 */
public interface AutoImportOptionsProvider extends UnnamedConfigurable {

  ExtensionPointName<AutoImportOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.autoImportOptionsProvider");

}
