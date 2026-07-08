class Test {
  String test(double d) {
      i<caret>f (d == Double.NaN) {
        return "nan";
      } else if (d == 1.5) {
        return "one and half";
      } else {
        return "other";
      }
    }
}
