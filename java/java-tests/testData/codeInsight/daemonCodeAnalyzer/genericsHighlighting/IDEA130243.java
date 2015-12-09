class Test {

  public enum EXT {
    TOP, BOTTOM
  }

  public static <C extends Comparable<? extends C>> C min(C c1, C... c2) {
    return null;
  }

  public static <C extends Comparable<? extends C>> C min(EXT ext, C c1, C... c2) {
    return null;
  }

  public static void main(String[] args) {
    min("a", "b", "c");
    min(EXT.TOP, "a", "b", "c");
  }
}