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
// IDEA-186321
class ForLoop {
  private final int i;
  {
    i = 1;
    for(;;i = 2, <error descr="Variable 'i' might already have been assigned to">i</error> = 3) {
      break;
    }
  }

  private final int j;
  {
    for(;;j = 2) {
      <error descr="Variable 'j' might already have been assigned to">j</error> = 1;
      break;
    }
  }
}
// IDEA-186305
class Asserts {
  final int x;
  {
    x = 1;
    assert true : x = 2;
  }

  final int x1;
  {
    x1 = 1;
    assert false : <error descr="Variable 'x1' might already have been assigned to">x1</error> = 2;
  }

  final int y;
  {
    try {
      assert true : y = 2;
    }
    catch (Throwable t) {}
    // javac accepts this, though this looks strange
    <error descr="Variable 'y' might already have been assigned to">y</error> = 1;
  }

  final int y1;
  {
    try {
      assert false : y1 = 2;
    }
    catch (Throwable t) {}
    <error descr="Variable 'y1' might already have been assigned to">y1</error> = 1;
  }
}
// IDEA-186304
class IncrementInUnreachableBranch {
  private final int i;
  {
    if (true) {
      i = 2;
    } else {
      System.out.println(i); // unreachable
      i++;
    }
  }

  private final int j;
  {
    if (true) {
      j = 2;
    } else {
      System.out.println(j); // unreachable
      j = j + 1;
    }
  }
}
class AssignmentInUnreachablePolyadic {
  private final boolean b;
  {
    if (false && (b = false)) ;
    if (true && (<error descr="Variable 'b' might already have been assigned to">b</error> = false)) ;
  }

  <error descr="Variable 'bb' might not have been initialized">private final boolean bb</error>;
  {
    if (false && (bb = false) && (<error descr="Variable 'bb' might already have been assigned to">bb</error> = true)) ;
  }

  private final boolean bbb;
  {
    if (false && (bbb = true)) {

    } else {
      <error descr="Variable 'bbb' might already have been assigned to">bbb</error> = false;
    }
  }

  private final boolean bbbb;
  {
    if (false && (bbbb = false) && (bbbb = true)) ;
    else if (true && (<error descr="Variable 'bbbb' might already have been assigned to">bbbb</error> = false)) ;
  }
}

class QualifiedThis {
  final int x;

  QualifiedThis() {
    <error descr="Cannot assign a value to final variable 'x'">QualifiedThis.this.x</error> = 5;
    this.x = 5;
  }
}

class ParenthesizedThis {
  final int x;
  final int y = <error descr="Variable '(this).x' might not have been initialized">(this).x</error> + 1;

  ParenthesizedThis() {
    (this).x = 5; // javac disallows this -- probably a bug in javac
    <error descr="Variable 'x' might already have been assigned to">this.x</error> = 6;
  }
}