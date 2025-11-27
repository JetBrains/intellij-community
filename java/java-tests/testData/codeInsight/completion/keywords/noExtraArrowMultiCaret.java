package bar;

public class Outer {
  private static final String SOME_CONSTANT = "someConstant";
  public static void main(String[] args) {
    String someString = SO<caret>;
    switch(args[0]) {
      case SO<caret> -> System.out.println(someString);
    }
  }
}