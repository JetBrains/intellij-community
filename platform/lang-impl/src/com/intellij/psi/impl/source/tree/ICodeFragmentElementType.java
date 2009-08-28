package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public abstract class ICodeFragmentElementType extends IFileElementType {
  public ICodeFragmentElementType(@NonNls final String debugName, final Language language) {
    super(debugName, language);
  }
}
