public class StringEqualityNewStringInMethod {
  // IDEA-265089
  public boolean bad() {
    return "baah".equals(toString("baah".getBytes("UTF-8")));
  }

  public boolean good() {
    return "baah".equals(new String("baah".getBytes("UTF-8"), 0, 4, "UTF-8"));
  }

  public boolean good2() {
    return toString("baah".getBytes("UTF-8")).equals("baah");
  }

  private static String toString(final byte[] data) {
    return new String(data, 0, 4, "UTF-8");
  }
}