// vars double initialization
import java.io.*;
import java.net.*;
public class a21  {

  void f1(int i) {
    final int j;
    j = 2;
    <error descr="Variable 'j' might already have been assigned to">j</error> = 2;
  }
  void f2(int i) {
    final int j;
    if (i==3) j = 2;
    else j = 5;
    <error descr="Variable 'j' might already have been assigned to">j</error> = 2;
  }
  void f3(int i) {
    final int j;
    if (i==4) j = 2;
    <error descr="Variable 'j' might already have been assigned to">j</error> = 2;
  }
  void f5(int i) {
    final int j;
    j = 2;
    if (i==3) return;
    <error descr="Variable 'j' might already have been assigned to">j</error> = 2;
  }
  void f6(int i) {
    final int j;
    switch (i) {
    case 1: j = 2;
    }
    <error descr="Variable 'j' might already have been assigned to">j</error> = 2;
  }
  void f7(int i) {
    final int j;
    while (i < 4) {
      <error descr="Variable 'j' might be assigned in loop">j</error> = 2;
      final int ii = 4;
      i+=ii;
    }
    
  }
  void f8(String k) {
        if (k != null) {
        final String i;
        if (k.equals("!")) i = "3";
        if (k.equals("!")) <error descr="Variable 'i' might already have been assigned to">i</error> = "2";
        }

  }

  void f9() {
    final Object type;
    try {
      type = null;    
    }
    catch (Exception e) {
      <error descr="Variable 'type' might already have been assigned to">type</error> = null;
    }
  }

  void f10() {
        final int k;
        if (false) {
            k=0;
            //< error descr="Variable 'k' might already have been assigned to">k< /error>=0;
        }
  }

class Foo {
    final int k;
    Foo() {
        k=0;
        <error descr="Variable 'k' might already have been assigned to">k</error>=0;
    }
}




  void cf1(int i) {
    final int j;
    final int j1 = 3;
    j = 5;
    final int unused;
    final int j2;
    if (j == 3) j2 = 4;
    final int j3;
    if (j==4) j3 = 5;
    else j3 = 6;
    final int j4 = j3 + 6;
    final int j5;
    while (i != 9) {
      if (j == 8) {
        j5 = 9;
        break;
      }
    }
  }

  final boolean FB = true;

  void cf2() {
      final int k;
      if (!FB) {
        k = 4;
      }
      // < error descr="Variable 'k' might already have been assigned to">k< /error>=0;
  }


    // todo:
    // in IDEA Variable 'b' might not have been initialized
    // in javac: OK
    /*
    void f2() {
        boolean b;
        boolean c = true;
        if (c && false) {
            c = b;
        }
    }
    */

}


class A {
    final int k;
    A() {
        for (;;) {
            <error descr="Variable 'k' might be assigned in loop">k</error>=0;
        }
    }
}

class Example {
    public int method(boolean b) {
        if (b) {
          final int indent;
          indent = 0;

          return 0;
        }
        else {
          new <error>Runnable</error>(){}<EOLError/>
        }
    <error>}</error>
}