// IDEA-191876
class Test {
  public static boolean is(double[] values) {
    double absSum = 0;
    double min = Double.NaN;
    double max = Double.NaN;

    for (double d : values) {
      if (!(d <= max))
        max = d;
      if (!(d >= min))
        min = d;
      absSum += Math.abs(d);
    }

    return absSum == 0; // should not be highlighted as always 0
  }
}