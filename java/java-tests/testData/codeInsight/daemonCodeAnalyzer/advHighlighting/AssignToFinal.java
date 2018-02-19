// assign to final
import java.io.*;

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
class T3 {
  private final boolean b;
  {
    assert false : "" + (b = true); // if assignment reachable, assert will not complete normally
    b = true; // compiles
    System.out.println(b);
  }
}
class T3a {
  private final boolean b;
  {
    try {
      assert false : "" + (b = true);
    }
    catch(IllegalArgumentException t) {}
    <error descr="Variable 'b' might already have been assigned to">b</error> = true; // red
    System.out.println(b);
  }
}
class T29 {
  // IDEA-186306
  private final int j;
  T29 (int b) {
    do {
      j = 34; // guaranteed to only be executed once
      if (true) break;
    } while (b == 1);
  }
}
class T29a {
  private final int j;
  T29a (int b) {
    do {
      <error descr="Variable 'j' might be assigned in loop">j</error> = 34; // not guaranteed by JLS to only be executed once
      if (j > 0) break;
    } while (b == 1);
  }
}
class TX {
  private final int i;
  private final int j;
  private final int k;
  TX() {
    (i) = 1;
    (<error descr="Variable 'i' might already have been assigned to">i</error>) = 1;
    (j) = 1;
    (<error descr="Variable 'j' might already have been assigned to">j</error>)++;
    (<error descr="Variable 'k' might not have been initialized">k</error>)++;
  }
}
