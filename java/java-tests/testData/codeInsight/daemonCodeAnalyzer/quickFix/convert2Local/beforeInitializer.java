// "Convert field to local variable in initializer section" "true"
class TestInitializer {

  private boolean fie<caret>ld;

  {
    field = true;
    System.out.println(field);
  }

}