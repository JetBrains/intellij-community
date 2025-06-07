import java.util.ArrayList;

class Main {
  private int  <warning descr="Field can be converted to a local variable">i</warning>;

  void test() {
    i = 10;
    i++;
    ArrayList<String> list = new ArrayList<String>(i) {}  ; // this use mistakenly turns off the 'field can be local' inspection
    System.out.println(i);
  }
}