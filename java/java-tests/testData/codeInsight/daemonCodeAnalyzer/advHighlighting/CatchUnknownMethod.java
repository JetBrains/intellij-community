public class MyClass {

 public static void main(String[] args) {
    try {
      <error>unknownMethod</error>(); /* Red code - as expected  */
    } catch (java.io.IOException e) { /* Unwanted red squiggly line  */
      e.printStackTrace();
    }
  }

}
