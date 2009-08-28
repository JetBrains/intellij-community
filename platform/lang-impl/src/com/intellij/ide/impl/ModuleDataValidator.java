package com.intellij.ide.impl;

import com.intellij.openapi.module.Module;

/**
 * @author yole
 */
public class ModuleDataValidator extends DataValidator<Module> {
  public Module findInvalid(String dataId, Module data, Object dataSource) {
    return data.isDisposed() ? data : null;
  }
}
