package com.intellij.usages.rules;

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.usages.Usage;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 26, 2004
 * Time: 9:22:35 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageInLibrary extends Usage {
  OrderEntry getLibraryEntry();
}
