package com.intellij.usages.rules;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usages.Usage;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004
 * Time: 5:21:01 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageInFile extends Usage {
  VirtualFile getFile();
}
