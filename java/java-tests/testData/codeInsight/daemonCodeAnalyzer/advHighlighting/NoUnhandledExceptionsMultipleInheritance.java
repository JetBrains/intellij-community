import java.io.*;

interface ThrowsCloneNotSupportedException {
  void f() throws CloneNotSupportedException;
}

interface ThrowsIOException {
  void f() throws IOException;
}

abstract class ThrowsNothing implements ThrowsCloneNotSupportedException, ThrowsIOException {
  private void foo() {
    f();
  }
}

class Main {
  public static void main(String[] args) {
    ThrowsNothing throwsNothing = null;
    throwsNothing.f();
  }
}


interface A {
  void close() throws Exception;
}

interface B {
  void close() throws IOException;
}

abstract class AB implements A, B {}
abstract class BA implements B, A {}

class ABUsage {
  void foo(AB ab) {
    try {
      ab.close();
    }
    catch (IOException ignored) {}
  }
  
  void foo(BA ba) {
    try {
      ba.close();
    }
    catch (IOException ignored) {}
  }
}

interface C {
  void close();
}

interface D {
  void close() throws IOException;
}

abstract class CD implements C, D {}
abstract class DC implements D, C {}

class CDUsage {
  void foo(CD cd) {
    try {
      cd.close();
    }
    catch (<error descr="Exception 'java.io.IOException' is never thrown in the corresponding try block">IOException ignored</error>) {}
  }
  
  void foo(DC dc) {
    try {
      dc.close();
    }
    catch (<error descr="Exception 'java.io.IOException' is never thrown in the corresponding try block">IOException ignored</error>) {}
  }
}