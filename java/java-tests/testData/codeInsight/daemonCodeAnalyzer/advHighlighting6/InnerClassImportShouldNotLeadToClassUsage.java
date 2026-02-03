package p;

import static p.Outer.Inner;
import static p.Outer1.InnerEnum.A;
import static p.OuterEnum.B;
import java.util.*;

abstract class <warning descr="Class 'Outer' is never used">Outer</warning> implements List<Inner> {
  public static class Inner {} 
}

class Outer1 {
  enum InnerEnum {
    A;
  }
  
  Object o = A;

  public static void main(String[] args) {
    System.out.println(new Outer1().o);
  }
}

enum OuterEnum {
  B;
}
class Outer2{
  Object o = B;

  public static void main(String[] args) {
    System.out.println(new Outer2().o);
  }
}