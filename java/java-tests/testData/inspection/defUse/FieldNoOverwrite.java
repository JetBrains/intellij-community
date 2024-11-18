public class FieldNoOverwrite {
  private String myField;
  private String myNext;

  void use() {
    myField = myNext;
    myNext = null;
  }

  void use2() {
    myField = myNext;
    myNext = null;
    System.out.println(myField);
  }
}