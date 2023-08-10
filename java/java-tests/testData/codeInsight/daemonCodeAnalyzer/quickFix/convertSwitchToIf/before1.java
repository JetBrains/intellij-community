// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void foo(float f) {
    switch (f<caret>) {
      case 0:
        System.out.println(f);
    }
  }
}