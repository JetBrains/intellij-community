class Test {
  String test(double d) {
      i<caret>f (d == 0.0) {
        return "zero";
      } else if (d == 1.5) {
        return "one and half";
      } else {
        return "other";
      }
    }
}
