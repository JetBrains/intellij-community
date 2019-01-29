// "Convert to local" "true"
class TestInitializer {

  private boolean fie<caret>ld;

  {
    field = true;
    System.out.println(field);
  }

}