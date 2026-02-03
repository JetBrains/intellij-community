class MyException extends Exception {}

class Test {
    void test() throws MyException {
        try {
          throw new RuntimeException();
        } catch(Exception e) {
          throw (MyException) <caret>
        }
    }
}