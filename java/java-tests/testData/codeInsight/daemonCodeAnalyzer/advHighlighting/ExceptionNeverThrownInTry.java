import java.io.*;


////////////
class x {
  void f() {
    try {
      int i = 0;
    }
    catch (<error descr="Exception 'java.io.IOException' is never thrown in the corresponding try block">IOException e</error>) {
    }
  }
}
//////////////////
class A {
  private void a() {
  }
}

class B extends A {
  void a() throws InterruptedException {
  }

  void b() {
    try {
      a();
    } catch (InterruptedException e) {
      // IDEA-207296
    }
  }
}