package com.intellij.ide.util.frameworkSupport;

import java.util.EventListener;

/**
 * @author nik
 */
public interface FrameworkSupportConfigurableListener extends EventListener {

  void frameworkVersionChanged();

}
