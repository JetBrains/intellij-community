// "Create field 'JANUARY'" "false"
class ExistingField {

  public record Month1(Integer JANUARY) {
  }

  public static void someMethod(){
    check("Some", Month1.JANUARY<caret>, 17);
  }

  private static void check(String some, Integer january, int i) {
  }
}