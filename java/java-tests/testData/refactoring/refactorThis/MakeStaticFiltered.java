public class Test {

    int y = 55;

    void foo(int x){
      System.out.println(x + y);
    }

    void test(){
      foo<caret>(42);
    }
}
