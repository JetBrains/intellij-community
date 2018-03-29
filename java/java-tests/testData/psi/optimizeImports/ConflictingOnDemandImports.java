package foo;

import static foo.C.*;
import static foo.D.*;

import foo.B.A;

class C {
  public static class A {}
  public static class C1 {}
  public static class C2 {}
  public static class C3 {}
  public static class C4 {}
  public static class C5 {}
}

class D {
  public static class A {}
  public static class D1 {}
  public static class D2 {}
  public static class D3 {}
  public static class D4 {}
  public static class D5 {}
}

class B {
  public static class A { }
  
  A aField;
  C1 c;
  C2 c1;
  C3 c2;
  C4 c3;
  C5 c4;
  D1 d;
  D2 d1;
  D3 d2;
  D4 d3;
  D5 d4;
}