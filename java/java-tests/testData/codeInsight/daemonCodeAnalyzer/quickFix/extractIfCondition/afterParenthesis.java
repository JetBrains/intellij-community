// "Extract if (b)" "true"
class MyTest {

  void foo(boolean a, boolean b, boolean c, boolean d) {
      if (b)
          if (a && (c || d)) {

          }
  }
}