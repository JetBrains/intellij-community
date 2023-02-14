import java.util.ArrayList;

public class ForStaticNestedNew {

  class Scratch {
    public static void main(String[] args) {
      new ArrayList<<caret>>()
    }
  }

  class String {}
}