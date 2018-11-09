import java.util.*;


class Simple {
  public static void main(String[] args) {
    List<Long> a = new ArrayList<>();
    List<String> b = new ArrayList<>();
    a.retainAll(<warning descr="'List<Long>' may not contain objects of type 'String'">b</warning>);
  }
}