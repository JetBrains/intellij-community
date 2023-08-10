public class Clazz {
  public static void main(String[] args) {
    doSmth((i, j) -> System.out.println(i + j));
  }

  static void doSmth(Child child) {
    child.execute(123, 456);
  }
}

interface Parent {
  void execute(int i, int j);
}

interface Child extends Parent {
  void execute(int i, int j);
}