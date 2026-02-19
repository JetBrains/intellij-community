class ArrayObjectsEquals {

  boolean one(String[] ss1, String[] ss2) {
    return java.util.Objects.<warning descr="Arrays comparison should probably be done using 'Arrays.equals()'">equals</warning>(ss1, ss2);
  }

  boolean two(String[][] ss1, String[][] ss2) {
    return java.util.Objects.<warning descr="Arrays comparison should probably be done using 'Arrays.deepEquals()'">equals</warning>(ss1, ss2);
  }

  boolean three(String[][] ss1, String[][] ss2) {
    return java.util.Arrays.<warning descr="Arrays comparison should probably be done using 'Arrays.deepEquals()'">equals</warning>(ss1, ss2);
  }

  int four(String[][] ss) {
    return java.util.Arrays.<warning descr="Array hash code calculation should probably be done using 'Arrays.deepHashCode()'">hashCode</warning>(ss);
  }

  boolean noWarn1(String s1, String[] ss2) {
    return java.util.Objects.equals(s1, ss2);
  }

  boolean noWarn2(String[] ss1, String[] ss2) {
    return java.util.Arrays.equals(ss1, ss2);
  }

  int noWarn3(String[] ss) {
    return java.util.Arrays.hashCode(ss);
  }

  boolean noWarn4(String[][] ss1, String[][] ss2) {
    return java.util.Objects.deepEquals(ss1, ss2);
  }

  boolean noWarn5(String[][] ss1, String[][] ss2) {
    return java.util.Arrays.deepEquals(ss1, ss2);
  }

  int noWarn6(String[][] ss) {
    return java.util.Arrays.deepHashCode(ss);
  }
}