package com.intellij.psi.impl.cache;

/**
 * @author max
 */ 
public interface ClassView extends DeclarationView {
  String getQualifiedName(long classId);
  boolean isInterface(long classId);
  boolean isAnonymous(long classId);
  boolean isAnnotationType (long classId);

  int getParametersListSize(long classId);
  String getParameterText(long classId, int parameterIdx);

  long[] getMethods(long classId);

  long[] getFields(long classId);

  long[] getInitializers(long classId);

  String getBaseClassReferenceText(long classId);
  boolean isInQualifiedNew(long classId);

  String[] getExtendsList(long classId);
  String[] getImplementsList(long classId);

  boolean isEnum(long classId);

  boolean isEnumConstantInitializer(long classId);
}
