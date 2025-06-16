package bar;

public class Outer {
  private static final String SOME_CONSTANT = "someConstant";
  public static void main(String[] args) {
      String someString = SOME_CONSTANT<caret>;
    switch(args[0]) {
        case SOME_CONSTANT<caret> -> System.out.println(someString);
    }
  }
}