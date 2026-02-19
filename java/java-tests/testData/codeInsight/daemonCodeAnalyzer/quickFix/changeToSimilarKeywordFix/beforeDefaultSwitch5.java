// "Fix the typo 'defaul' to 'default'" "true-preview"
public class Test {
  public static void test(List<String> list) {
    return switch (1) {
      case 1->{
        System.out.println();
      }
      defaul<caret>->{

      }
    }
  }
}