class HashCode {
  Object a, b, c;

  public int hashCode() {
    int result = 0;
    result = result * 31 + a.hashCode();
    result = result * 31 + b.hashCode();
    result = result * 31 + c.hashCode();
    return result;
  }

  public int hashCode2() {
    int result = 0;
    result = <warning descr="Result of 'result * 31' is always '0'">result * 31</warning> + a.hashCode();
    result = result * 31 + b.hashCode();
    result = result * 31 + c.hashCode();
    return result;
  }

  static final String STRING = "123456";
  static final String PREFIX = "123";
  
  void testIndexOf(String s) {
    int pos = STRING.indexOf(PREFIX);
    if (s.equals(PREFIX)) {
      int pos2 = <warning descr="Result of 's.indexOf(PREFIX)' is always '0'">s.indexOf(PREFIX)</warning>;
    }
  }
}
