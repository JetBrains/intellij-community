package com.intellij.psi.impl.cache;

/**
 * @author max
 */ 
public interface MethodView extends DeclarationView {
  boolean isConstructor(long methodId);
  boolean isVarArgs(long methodId);

  String getReturnTypeText(long methodId);

  int getParameterCount(long methodId);
  String getParameterName(long methodId, int paramIdx);
  String getParameterTypeText(long methodId, int paramIdx);
  boolean isParameterTypeEllipsis(long methodId, int paramIdx);

  int getTypeParametersCount(long methodId);
  String getTypeParameterText(long methodId, int paramIdx);

  String[] getThrowsList(long methodId);

  boolean isAnnotationMethod(long methodId);
  String getDefaultValueText(long methodId);
  String[][] getParameterAnnotations (long methodId);
}
