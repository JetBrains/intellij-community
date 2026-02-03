// "Create missing branch 'true'" "true-preview"
import java.util.List;

class Test {
  public static void main(String[] args) {
    test(true);
  }

  public static void test(boolean b) {
    switch (b){
        case true -> {
        }
        case false -> {
      }
    }
  }
}