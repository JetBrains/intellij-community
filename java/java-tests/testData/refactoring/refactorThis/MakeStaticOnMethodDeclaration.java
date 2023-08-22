public class Test {

    int y = 55;

    void foo<caret>(int x){
      System.out.println(x + y);
    }

    void test(){
      foo(42);
    }
}
