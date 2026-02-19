public class A {
  public static void main(String[] args) {
    A a = new A();
    if (a.foo() == 0) { }

    if (a.foo() == 0) { }

    if (a.foo() == 0) { //if with two lines
      System.out.println(0);
      System.err.print(1);
    }

    if (a.foo() == 0) { //if with one line
      System.out.println(0);
    }

    if (a.foo() == 1) { //if with one line
      System.out.println(1);
    }

    if (a.foo() == 0) { //if with one line with else with one line
      System.out.println(0);
    } else {
      System.out.println(1);
    }

    if (a.foo() == 0) { //if with one line with else with one line
      System.out.println(0);
    } else {
      System.out.println(1);
    }

    if (a.foo() == 0) { //if with one line with else with two lines
      System.out.println(0);
    } else {
      System.out.println(1);
      System.out.println(1);
    }
    while (a.foo() == 0) { //complex while
      System.out.println(1);
      System.out.println(1);
      System.out.println(1);
      System.out.println(1);
    }  }

  public int foo() {
    return 1;
  }


}