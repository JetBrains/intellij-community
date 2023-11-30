import java.util.List;

class FQLIst {

  public static void main(String[] args) {
      List<String> strings = getStrings();
      for (int i = 0, stringsSize = strings.size(); i < stringsSize; i++) {
          String s1 = strings.get(i);
          System.err.println(s1);
      }
  }

  public java.util.List<String> getStrings() {
    return null;
  }
}