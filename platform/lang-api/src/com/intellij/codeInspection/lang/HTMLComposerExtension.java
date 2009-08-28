/*
 * User: anna
 * Date: 21-Dec-2007
 */
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

public interface HTMLComposerExtension<T> {
  Key<T> getID();
  Language getLanguage();

  void appendShortName(RefEntity entity, final StringBuffer buf);

  void appendLocation(RefEntity entity, final StringBuffer buf);

  @Nullable
  String getQualifiedName(RefEntity entity);

  void appendReferencePresentation(RefEntity entity, final StringBuffer buf, final boolean isPackageIncluded);


}