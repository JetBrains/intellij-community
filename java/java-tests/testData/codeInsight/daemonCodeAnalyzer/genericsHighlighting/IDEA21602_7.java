import java.util.Collection;
import java.util.Collections;

class IDEABug {

  static class ClassA {
    static <T> void sayHello(Collection<? extends T> msg) {}
  }

  static class ClassB extends ClassA {
    <error descr="'sayHello(Collection<? extends T>)' in 'IDEABug.ClassB' clashes with 'sayHello(Collection<? extends T>)' in 'IDEABug.ClassA'; both methods have same erasure, yet neither hides the other">static <T extends String> void sayHello(Collection<? extends T> msg)</error> {}
  }

  public static void main(String[] args) {
    ClassB.sayHello(Collections.<String>emptyList());
  }
}