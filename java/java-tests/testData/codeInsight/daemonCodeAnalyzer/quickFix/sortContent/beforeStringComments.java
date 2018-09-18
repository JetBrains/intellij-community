// "Sort content" "true"

public class Test {
  static final String[] SPECIFICATIONS = new String[]{
    "1",
      "100",<caret>
      "100+10",
      "100..999"//simple end comment
      ,
      //simple end comment
      "100+3..999",
      "1",
      "1..900",
  };
}