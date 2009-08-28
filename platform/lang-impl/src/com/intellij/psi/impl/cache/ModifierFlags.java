package com.intellij.psi.impl.cache;

/**
 * @author max
 */
public interface ModifierFlags {
// ---- Group equals to correspoding in ClsUtil's ACC_*
  int PUBLIC_MASK = 0x0001;
  int PRIVATE_MASK = 0x0002;
  int PROTECTED_MASK = 0x0004;
  int STATIC_MASK = 0x0008;
  int FINAL_MASK = 0x0010;
  int SYNCHRONIZED_MASK = 0x0020;
  int VOLATILE_MASK = 0x0040;
  int TRANSIENT_MASK = 0x0080;
  int NATIVE_MASK = 0x0100;
  int INTERFACE_MASK = 0x0200;
  int ABSTRACT_MASK = 0x0400;
// ---- End of the group equals to correspoding in ClsUtil's ACC_*

  int STRICTFP_MASK = 0x0800;
  int PACKAGE_LOCAL_MASK = 0x1000;
  int DEPRECATED_MASK = 0x2000;
  int ENUM_MASK = 0x4000;
  int ANNOTATION_TYPE_MASK = 0x8000;
  int ANNOTATION_DEPRECATED_MASK = 0x10000;
}
