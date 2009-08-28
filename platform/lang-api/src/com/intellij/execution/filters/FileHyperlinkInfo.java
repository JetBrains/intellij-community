package com.intellij.execution.filters;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;

/**
 * @author yole
 * @since 7.0.3
 */
public interface FileHyperlinkInfo extends HyperlinkInfo {
  OpenFileDescriptor getDescriptor();
}
