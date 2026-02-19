public class Clazz {
  public static void main(String[] args) {
    doSmth(System.out::println);
  }

  static void doSmth(Child child) {
    child.execute(123);
  }
}

interface Parent {
  void execute(int i);
}

interface Child extends Parent {
  void execute(int i);
}