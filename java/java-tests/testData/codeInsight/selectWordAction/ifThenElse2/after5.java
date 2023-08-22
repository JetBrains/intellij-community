package test;

public class TestClass {

  public void test() {
	  if (true) {
          System.out.println();
	  }
<selection>      else if (false) {
          System.out.println();
      }
      else {
<caret>         System.out.println();
	  }
</selection>  }
}