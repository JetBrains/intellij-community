import java.util.Collection;
import java.util.Collections;

class IDEABug {

  static class ClassA {
    static <T> void sayHello(Collection<? extends T> msg) {}
  }

  static class ClassB extends ClassA {
    static <T extends String> void sayHello(Collection<? extends T> msg) {}
  }

  public static void main(String[] args) {
    ClassB.sayHello(Collections.<String>emptyList());
  }
}