// "Convert field to local variable in method 'someMethod'" "true-preview"
class TestFieldConversion
{

    public void someMethod(int s) {
    switch (s) {
      case 1:
        System.out.println(someInt);
        break;
      case 3:
        System.out.println(someInt);
        break;
      default:
        break;
    }
  }
}
