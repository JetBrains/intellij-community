// "Create enum 'Foo'" "true-preview"
public interface Test {
  default void foo(java.util.List<? extends Test> l){
    if (l.get(0) instanceof Foo) {}
  }
}

public enum Foo {}