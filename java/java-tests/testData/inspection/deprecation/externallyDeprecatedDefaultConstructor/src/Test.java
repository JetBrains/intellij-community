import a.A;

//Here "Default constructor in A is deprecated" warning must be reported.
public class Test extends A {
}

class Test2 {
  public void foo() {
    //Here "Default constructor in A is deprecated" warning must be reported.
    new A() {}
  }
}

class Test3 extends A {
  //Here "Default constructor in A is deprecated" warning must be reported.
  public Test3() {
    super();
  }
}