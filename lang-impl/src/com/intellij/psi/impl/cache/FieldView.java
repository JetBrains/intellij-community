package com.intellij.psi.impl.cache;



/**
 * @author max
 */ 
public interface FieldView extends DeclarationView {
  long getFirstFieldInDeclaration(long fieldId);

  String getTypeText(long fieldId);

  String getInitializerText(long fieldId) throws InitializerTooLongException;

  boolean isEnumConstant (long fieldId);

  long getEnumConstantInitializer(long fieldId);
}
