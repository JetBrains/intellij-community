// "Ex4" "true"

class Hello {
  void f() throws <caret>Ex4 {}
  void g() throws Ex2 { throw new Ex2();}
  {
    try (A a = new A()) {
      f();
      f();
      g();
      throw new Ex1();
    } catch (Ex1 | Ex2 | Ex3 | Ex4 ex2) {
      ex2.printStackTrace();
    } catch (Ex1 | Ex4 ex4) {
      ex4.printStackTrace();
    } catch (Ex4 e) {}
  }
}

class Ex1 extends Exception {}
class Ex2 extends Ex1 {}
class Ex3 extends Exception {}
class Ex4 extends Exception {}

class A implements AutoCloseable {
  public A() throws Ex3 {
    throw new Ex3();
  }

  @Override
  public void close() throws Ex1 {
    throw new Ex1();
  }
}
