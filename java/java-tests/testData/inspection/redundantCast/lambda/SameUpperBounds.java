class MyTest {
  void createComparator(NodeDescriptor<?> o) {
    final String s = ((NodeDescriptor1<?>) o).get().substring(2);
  }

  interface NodeDescriptor<T> {
    T get();
  }
  interface NodeDescriptor1<K extends String> extends NodeDescriptor<K> {
    K get();
  }
}