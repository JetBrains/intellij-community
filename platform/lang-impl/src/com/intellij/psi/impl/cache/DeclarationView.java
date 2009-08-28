package com.intellij.psi.impl.cache;



/**
 * @author max
 */
public interface DeclarationView extends RepositoryItemView {
  int getModifiers(long id);
  boolean isDeprecated(long id);
  boolean mayBeDeprecatedByAnnotation(long id); //for source elements only, for cls the value of the attribute is written 
  long[] getInnerClasses(long id);
  String[] getAnnotations (long id);
}
