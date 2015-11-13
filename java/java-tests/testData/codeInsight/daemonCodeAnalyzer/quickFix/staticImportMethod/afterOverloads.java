// "Static import method..." "true"
package foo;

import static foo.B.a;

public class X {
    {
        a("");
    }
}

class B {
  
  public static  Integer a(Integer i) {
    return 1;
  }

  public static  Integer a() {
    return 1;
  }

  public static  Integer a(String s) {
    return 1;
  }
  
  public static  Integer a(String s, String s) {
    return 1;
  }
}

class A {
  public static  Integer a(String s) {
    return 1;
  }
}
