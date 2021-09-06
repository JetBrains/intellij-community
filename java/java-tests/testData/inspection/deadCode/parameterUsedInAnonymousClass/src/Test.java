public class Clazz {
  public static void main(String[] args) {
    doSmth(new Child() {
      @java.lang.Override
      public void execute(int i) {
        System.out.println(i + 1);
      }
    });
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