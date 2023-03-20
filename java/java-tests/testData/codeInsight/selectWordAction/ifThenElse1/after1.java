package test;

public class TestClass {

  public void test() {
	  if (true) {
          System.out.println();
	  }
      else {
<caret><selection>         System.out.println();
</selection>	  }
  }
}