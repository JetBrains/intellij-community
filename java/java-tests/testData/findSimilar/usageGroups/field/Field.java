import java.util.ArrayList;

public class Test {
  int field;
  Test(){
    this.field = 0;
  }

  private int test(int num){
    return num;
  }

  private void testFields(){
    int a = 1;
    test(a);
    test(field);
  }

  private void testIfWithFields(){
    int a = 1;

    if(tets(a) == 5){
      System.out.println("variable");
    }

    if(test(field) == 5){
      System.out.println("field");
    }

    if(test(a) == 5){
      System.out.println("field");
    }

    if(test(field) != 5){
      System.out.println("field");
    }
  }
}
