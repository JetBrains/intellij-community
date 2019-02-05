// "Convert field to local variable in constructor" "true"
class Test {

  private final String f<caret>ield;

  public Test(String param) {
    field = param;
    System.out.println(field == null ? "null" : field);
  }

}