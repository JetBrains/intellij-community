package com.intellij.codeInsight.intention.impl.config;

import java.io.IOException;

/**
 * @author yole
 */
public interface TextDescriptor {
  String getText() throws IOException;
  String getFileName();
}
