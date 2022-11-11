public class ForStaticNestedNew {

  class Scratch {
    public static void main(String[] args) {
      java.util.ArrayList.new<caret>
    }
  }

  class String {}
}