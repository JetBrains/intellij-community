// "Create missing branch 'false'" "true-preview"
import java.util.List;

class Test {
  public static void main(String[] args) {
    test(true);
  }

  private static final boolean yes = true;

  public static void test(boolean b) {
    switch (b<caret>){
      case yes -> {
      }
    }
  }
}