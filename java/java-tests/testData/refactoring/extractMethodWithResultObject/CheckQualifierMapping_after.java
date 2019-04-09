import java.util.*;
class Test {

  public void method() {
    String a = "A";
      NewMethodResult x = newMethod(a);

      ArrayList<String> listB = new ArrayList<String>();
    ArrayList<String> listC = new ArrayList<String>();
    listB.add("B");
    listC.add("C");
  }

    NewMethodResult newMethod(String a) {
        ArrayList<String> listA = new ArrayList<String>();
        listA.add(a);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}