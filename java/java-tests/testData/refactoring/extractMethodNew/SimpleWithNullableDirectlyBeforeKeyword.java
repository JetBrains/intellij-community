public class Test {
    public void test(){
      String a;
      <selection> if (1 == 0) {
        a = "1";
      } else {
        a = null;
      }</selection>
      return a;
    }
}