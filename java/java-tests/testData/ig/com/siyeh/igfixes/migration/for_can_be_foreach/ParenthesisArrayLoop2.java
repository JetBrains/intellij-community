class X {

  void x(String[] array) {
    <caret>for (int i = (0), max = ((array).length); (i) < (max); (i)++) {
      out.println(((array)[(i)]));
    }
  }
}