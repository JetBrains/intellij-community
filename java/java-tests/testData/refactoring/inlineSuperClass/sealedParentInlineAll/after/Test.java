final class Test {
    void doSmth() {
      System.out.println("hello");
    }
}

class Test1 {
    void doSmth() {
      System.out.println("hello");
    }
}

sealed class Test2 permits Test3 {
    void doSmth() {
      System.out.println("hello");
    }
}

final class Test3 extends Test2 {}

