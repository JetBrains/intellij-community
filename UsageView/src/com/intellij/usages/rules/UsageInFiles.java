package com.intellij.usages.rules;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2004
 * Time: 4:51:02 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageInFiles {
  VirtualFile[] getFiles();
}
