import java.util.*;
class Test
{
  public static void main(String[] args) {
    S[] stringArray = {};
    Arrays.sort(stringArray, (s, s2) -> s.comp(s2));
  }


  static class S {

    int comp(S s) {return 0;}
  }
}
