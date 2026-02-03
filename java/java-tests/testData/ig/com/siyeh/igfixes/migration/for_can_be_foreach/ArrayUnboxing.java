
class ArrayUnboxing {

  void m(Integer[] values) {
    for<caret> (int i = 0; i < values.length; i++) {
      int value = values[i];
      if (value == Integer.valueOf(100000)) {
        throw null;
      }
    }
  }
}