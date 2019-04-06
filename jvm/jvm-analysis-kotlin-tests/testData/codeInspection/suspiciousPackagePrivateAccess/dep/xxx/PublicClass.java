// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package xxx;

public class PublicClass {
  public String publicField;
  String packagePrivateField;
  static public String PUBLIC_STATIC_FIELD;
  static String PACKAGE_PRIVATE_STATIC_FIELD;

  public void publicMethod() {}
  void packagePrivateMethod() {}

  PublicClass() {}
  public PublicClass(int i) {}
  PublicClass(boolean b) {}
}