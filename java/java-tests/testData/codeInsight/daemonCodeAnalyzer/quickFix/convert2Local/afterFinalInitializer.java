// "Convert field to local variable in initializer section" "true-preview"
class TestInitializer {

    {
        final boolean field = true;
    System.out.println(field);
  }

}