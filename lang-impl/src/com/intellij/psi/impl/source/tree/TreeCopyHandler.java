/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;

import java.util.Map;

public interface TreeCopyHandler {
  void encodeInformation(TreeElement element, ASTNode original, Map<Object, Object> encodingState);
  TreeElement decodeInformation(TreeElement element, Map<Object, Object> decodingState);
}