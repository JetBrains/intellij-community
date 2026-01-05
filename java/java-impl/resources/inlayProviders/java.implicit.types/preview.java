import java.util.*;

class HintsDemo {

  public static void main(String[] args) {
    var list/*<# : List<String> #>*/ = getList();
  }

  private static List<String> getList() {
    return Arrays.asList("hello", "world");
  }
}
