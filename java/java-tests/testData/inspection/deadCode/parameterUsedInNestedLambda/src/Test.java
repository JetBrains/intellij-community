public class Clazz {
  public static void main(String[] args) {
    doSmth(k -> doSmth(i -> test(i + 1)));
  }

  static void doSmth(Child child) {
    child.execute(123);
  }

  static void test(int i) {
    System.out.println(i);
  }
}

interface Parent {
  void execute(int i);
}

interface Child extends Parent {
  void execute(int i);
}