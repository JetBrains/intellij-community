// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(float f) {
    switch (f<caret>) {
      case 0:
        System.out.println(f);
    }
  }
}