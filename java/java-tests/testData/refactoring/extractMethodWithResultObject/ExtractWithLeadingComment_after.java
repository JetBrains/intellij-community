import java.util.List;

class Test {
    List list2;

    public void method2() {
      if (true) {
          NewMethodResult x = newMethod();

      } else {

      }
    }

    NewMethodResult newMethod() {// add to list2
        list2.add(true);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}