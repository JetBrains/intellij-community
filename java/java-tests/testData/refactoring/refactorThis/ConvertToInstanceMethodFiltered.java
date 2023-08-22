public class Test {

    int x;

    static void foo(int y){
      System.out.println(x + y);
    }

    void test(){
      foo<caret>(y);
    }
}
