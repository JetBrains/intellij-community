void main() {

}

int field;

public void run() {
  class X {
    void test(int a) {
      String var<caret> = "";
      System.out.println(field);
    }
  }
}