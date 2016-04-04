class Bar {
  int zoooa() {}
  int zooob() {}

  int foo() {
    return true ? zoooa()
                :<caret> 2;
  }
}
