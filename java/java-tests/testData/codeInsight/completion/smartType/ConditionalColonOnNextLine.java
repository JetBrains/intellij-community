class Bar {
  int zoooa() {}
  int zooob() {}

  int foo() {
    return true ? zoo<caret>
                : 2;
  }
}
