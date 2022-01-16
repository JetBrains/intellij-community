// "Change variable 'i' type to 'Integer'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case Integer i -> System.out.println("int");
    }
  }
}