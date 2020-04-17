import java.util.*;
import java.util.stream.Collectors;

class MyTest {
  static class Builder {
    Builder put(int i) { return this;}
    Builder put(long i) { return this;}
    Builder put(short i) { return this;}
    Builder put(double i) { return this;}
    Builder put(float i) { return this;}
    Builder put(boolean i) { return this;}
    Builder put(byte i) { return this;}
    Builder put(String i) { return this;}
    Builder put(Object i) { return this;}
  }

  void addAll(Object o) {}
  <T> List<T> addAll(List<T> l) { return l;}

  void m(List<Foo> list){
    add<caret>All(list.stream().map(it -> new Builder()
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar())
      .put(it.getBar()))
      .collect(Collectors.toList()));
  }

  static class Foo {
    String getBar() {
      return "";
    }
  }
}
