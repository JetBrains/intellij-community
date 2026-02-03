
class FQLIst {

  public static void main(String[] args) {
    <caret>for (String s1 : getStrings()) {
      System.err.println(s1);
    }
  }

  public java.util.List<String> getStrings() {
    return null;
  }
}