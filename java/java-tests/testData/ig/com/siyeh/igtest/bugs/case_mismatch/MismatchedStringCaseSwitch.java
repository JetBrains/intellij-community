class X {
  void test(String s1, String s2) {
    switch(s1.toLowerCase()) {
      case <warning descr="Switch branch is unreachable: the label contains an uppercase symbol while the selector is lowercase-only">"FOO"</warning>:
      case "foo":
      case <warning descr="Switch branch is unreachable: the label contains an uppercase symbol while the selector is lowercase-only">"Foo"</warning>:
      case "123":
      case "12f":
      case <warning descr="Switch branch is unreachable: the label contains an uppercase symbol while the selector is lowercase-only">"12F"</warning>:
    }
    switch("FOO") {
      case "foo": // processed by another inspection
    }
    switch(s2.toUpperCase()) {
      case "FOO":
      case <warning descr="Switch branch is unreachable: the label contains a lowercase symbol while the selector is uppercase-only">"foo"</warning>:
      case <warning descr="Switch branch is unreachable: the label contains a lowercase symbol while the selector is uppercase-only">"Foo"</warning>:
      case "123":
      case <warning descr="Switch branch is unreachable: the label contains a lowercase symbol while the selector is uppercase-only">"12f"</warning>:
      case "12F":
    }
  }
}