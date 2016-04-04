class Test {

  void f(String value) {
    final int index = -1;
    while ((<error descr="Cannot assign a value to final variable 'index'">index</error> = value.indexOf('a')) != -1) {
      value = value.substring(0, 1);
    }
  }

}