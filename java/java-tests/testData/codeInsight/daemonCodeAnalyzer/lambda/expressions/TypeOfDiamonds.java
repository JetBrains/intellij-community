import java.util.*;
class MyTest extends AbstractList {
  MyTest(String name) {
    super(name);
  }

  protected Task getF(List<String> files) {
    return new Foo<>(new ArrayL<caret>ist<>(files)) {
      @Override
      protected void a() { }
    };
  }

  private abstract class Foo<T extends String> extends Task<T> {
    public Foo(List<T> files) {
      super();
    }

    protected abstract void a();
  }

}

class Task<K> {
  protected Task() {
  }
  
  protected Task(String name) {}
}