import java.util.*;
class Test
{
  public static void main(String[] args) {
    S[] stringArray = {};
    Arrays.sort(stringArray, S::co<caret>mp);
  }


  static class S {

    int comp(S s) {return 0;}
  }
}
