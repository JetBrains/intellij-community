// "Convert field to local variable in method 'someMethod'" "true-preview"
class TestFieldConversion
{

    public void someMethod(int s) {
        /**
         * doc1
         */
        int someInt = 0;
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
