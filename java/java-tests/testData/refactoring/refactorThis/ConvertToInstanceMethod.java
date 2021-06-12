public class Test {

    int x;

    static void foo<caret>(int y){
      System.out.println(x + y);
    }

    void test(){
      foo(y);
    }
}
