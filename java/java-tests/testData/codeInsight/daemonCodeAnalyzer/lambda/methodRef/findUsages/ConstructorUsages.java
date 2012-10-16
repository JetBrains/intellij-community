class ConstructorUsages {
  ConstructorUsages() {
  }

  void foo() {
    BlahBlah<ConstructorUsages> blahBlah = ConstructorUsages::new;
  }
}
interface BlahBlah<T> {
        T foo();
}