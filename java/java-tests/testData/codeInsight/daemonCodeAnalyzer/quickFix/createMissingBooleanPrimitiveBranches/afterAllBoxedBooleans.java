// "Create missing branches 'true' and 'false'" "true-preview"
import java.util.List;

class Test {
  public static void main(String[] args) {
    test(true);
  }

  public static void test(Boolean b) {
    switch (b){
        case true -> {
        }
        case false -> {
        }
    }
  }
}