public class XXX {
  void f<caret>oo() {
    int i = 0 ;
    bar(i);
    System.out.println(i);
  }

  private void bar(int i){}
}