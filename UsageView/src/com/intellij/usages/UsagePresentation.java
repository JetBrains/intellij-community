package com.intellij.usages;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:22:51 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsagePresentation {
  TextChunk[] getText();
  Icon getIcon();
}
