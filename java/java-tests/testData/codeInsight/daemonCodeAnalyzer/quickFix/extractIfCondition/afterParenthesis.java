// "Extract if (b)" "true-preview"
class MyTest {

  void foo(boolean a, boolean b, boolean c, boolean d) {
      if (b)
          if (a && (c || d)) {

          }
  }
}