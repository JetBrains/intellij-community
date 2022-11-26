// "Merge with 'default'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case default, null -> System.out.println("hello");
    }
  }
}
