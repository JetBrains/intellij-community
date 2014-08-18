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
    <error descr="Cannot assign a value to final variable 'j'">j</error> = 3;
    
  }
  void f2(final int i) {
    final int j = 4;
    <error descr="Cannot assign a value to final variable 'i'">i</error> = 3;
    
  }
  void f3( int ip) {
    <error descr="Cannot assign a value to final variable 'fi'">fi</error> = 3;
    for (final int i = 0; i<3; <error descr="Cannot assign a value to final variable 'i'">i</error>++) {
      int k = 4;  
    }
    final int i1 = 0;
    <error descr="Cannot assign a value to final variable 'i1'">i1</error>++;
    --<error descr="Cannot assign a value to final variable 'i1'">i1</error>;
    int i2 = -i1 + ~i1;
    final int j = (<error descr="Cannot assign a value to final variable 'j'">j</error>=0) == 1 || j==0 ? 9 : j;
  }

  static final boolean DEBUG = false;
  void f4() {
    if (DEBUG && (fi < 3 || fi >4)) return;
  }

  void f5(final int i) {
    (<error descr="Cannot assign a value to final variable 'i'">i</error>) = 1;
  }
  void f6(final int i) {
    (<error descr="Cannot assign a value to final variable 'i'">i</error>)++;
  }
  void f7(final int i) {
    ++(<error descr="Cannot assign a value to final variable 'i'">i</error>);
  }
}
class B extends a21 {
  public B() {
    <error descr="Cannot assign a value to final variable 'fi'">fi</error> = 0;
  }
  void f() {
     final Integer i;
     new Runnable() {
         public void run() {
             <error descr="Cannot assign a value to final variable 'i'">i</error> = new Integer(4);
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
        <error descr="Cannot assign a value to final variable 'i'">i</error> = 0;
      }
    };
  }
}

class Foo {
  private final Foo next;

  public Foo(Foo previous) {
    this.next = null;

    if (previous != null) {
      <error descr="Cannot assign a value to final variable 'next'">previous.next</error> = this;
    }
  }
}
class T1 {
  private final int i1;
  private final int i2;
  private final int i3;
  private final int i4;
  {
    (<error descr="Variable 'i1' might not have been initialized">i1</error>)++;
    ++(<error descr="Variable 'i2' might not have been initialized">i2</error>);
    <error descr="Variable 'i3' might not have been initialized">i3</error> += 1;
    (i4) = 1;
    (<error descr="Expression expected">)</error>++;
  }
}