// assign to final
import java.io.*;
import java.net.*;
public class a21  {
  final int fi;
  {
   fi = 4;
  }
  void f1(int i) {
    final int j = 4;
    <error descr="Cannot assign a value to final variable 'j'">j = 3</error>;
    
  }
  void f2(final int i) {
    final int j = 4;
    <error descr="Cannot assign a value to final variable 'i'">i = 3</error>;
    
  }
  void f3( int ip) {
    <error descr="Cannot assign a value to final variable 'fi'">fi = 3</error>;
    for (final int i = 0; i<3; <error descr="Cannot assign a value to final variable 'i'">i++</error>) {
      int k = 4;  
    }
    final int i1 = 0;
    <error descr="Cannot assign a value to final variable 'i1'">i1++</error>;
    <error descr="Cannot assign a value to final variable 'i1'">--i1</error>;
    int i2 = -i1 + ~i1;
    final int j = (<error descr="Cannot assign a value to final variable 'j'">j=0</error>) == 1 || j==0 ? 9 : j;
  }

  static final boolean DEBUG = false;
  void f4() {
    if (DEBUG && (fi < 3 || fi >4)) return;
  }
}
class B extends a21 {
  public B() {
    <error descr="Cannot assign a value to final variable 'fi'">fi = 0</error>;
  }
  void f() {
     final Integer i;
     new Runnable() {
         public void run() {
             <error descr="Cannot assign a value to final variable 'i'">i = new Integer(4)</error>;
         }
     };
  }
}

class a21_2 {
  final int i;
  a21_2() {
    i = 0;
    new Runnable() {
      public void run() {
        <error descr="Cannot assign a value to final variable 'i'">i = 0</error>;
      }
    };
  }
}

class Foo {
  private final Foo next;

  public Foo(Foo previous) {
    this.next = null;

    if (previous != null) {
      <error descr="Cannot assign a value to final variable 'next'">previous.next = this</error>;
    }
  }
}
