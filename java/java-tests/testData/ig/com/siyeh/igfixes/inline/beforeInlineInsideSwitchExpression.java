// "Inline variable" "true-preview"
import java.util.List;

public class Demo {

  public static void main(String[] args) {
    var y = switch (1) {
      case 1:
        final int i<caret>  = 3;
        yield i;
      default:
        yield 4;
    };
  }
}