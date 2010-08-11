package pack;

import java.util.ArrayList;

public class MyClass extends ArrayList implements Cloneable{
  /**
   * @deprecated
   */
  static final int field1 = 123;

  Object[] field2;

  void method1(int[] p1, Object p2) throws Exception, java.io.IOException{
  }

  Integer method2()[]{
    return null;
  }

  MyClass(){}

  static class Inner{}
}
