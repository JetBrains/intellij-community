class Test {
  String test(double d) {
      i<caret>f (d == 1.5) {
        return "one and half";
      } else if (d == 2.5) {
        return "two and half";
      } else {
        return "other";
      }
    }
}
