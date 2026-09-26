
class ArrayUnboxing {

  void m(Integer[] values) {
      for (int value : values) {
          if (value == Integer.valueOf(100000)) {
              throw null;
          }
      }
  }
}