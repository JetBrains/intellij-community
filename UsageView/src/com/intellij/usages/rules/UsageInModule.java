package com.intellij.usages.rules;

import com.intellij.openapi.module.Module;
import com.intellij.usages.Usage;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:33:37 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageInModule extends Usage {
  Module getModule();
}
