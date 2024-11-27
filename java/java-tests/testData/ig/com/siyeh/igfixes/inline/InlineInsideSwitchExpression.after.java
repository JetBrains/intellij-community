import java.util.List;

public class Demo {

  public static void main(String[] args) {
    var y = switch (1) {
      case 1:
          yield <caret>3;
      default:
        yield 4;
    };
  }
}