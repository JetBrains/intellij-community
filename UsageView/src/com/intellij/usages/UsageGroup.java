package com.intellij.usages;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.pom.Navigatable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:27:00 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageGroup extends Comparable<UsageGroup>, Navigatable {
  Icon getIcon(boolean isOpen);
  String getText(UsageView view);
  FileStatus getFileStatus();
  boolean isValid();
}
