import java.util.List;

class Sample {
  <T extends List<K>, K extends List<T>> void foo(){}

  {
    foo();
  }
}