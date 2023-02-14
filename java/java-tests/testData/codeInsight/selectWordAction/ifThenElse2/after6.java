package test;

public class TestClass {

  public void test() {
<selection>	  if (true) {
          System.out.println();
	  }
      else if (false) {
          System.out.println();
      }
      else {
<caret>         System.out.println();
	  }
</selection>  }
}