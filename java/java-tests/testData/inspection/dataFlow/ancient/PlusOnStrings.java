import java.io.*;

class Foo {
  public void foo(Object a, String b) {
     String c = a + b;
     if (<warning descr="Condition 'c == null' is always 'false'">c == null</warning>) {
         System.out.println("Can't be!");
     }
  }
}