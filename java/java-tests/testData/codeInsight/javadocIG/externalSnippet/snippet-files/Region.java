public class Region {
  // @start region=main
  public static void main(String[] args) {
    // @replace region=copyright replacement=""
    /* Copyright
     * Blahblah
     */
    // @end
    code: // @replace regex=code: replacement="..."
    System.out.println("Hello"); // @highlight substring=Hello
    System.out.println("Whole line"); // @highlight type="highlighted"
    // @replace region replacement="    ...omitted..."
    System.out.println("First");
    System.out.println("Second");
    // @end
  }
  // @end
  
  // @start region=test
  public void test() {
    System.out.println("Region no markup");
  }
  // @end region=test
  
  // @start region=multitag
  public void test() {
    System.out.println("abc"); // @replace substring=a replacement=x @replace regex="b(.?)" replacement="$1z" @highlight substring=out
  }
  // @end
  
  // @start region=malformed
  public void malformed() { 
    // @replace @highlight hello=world @unknown
    // @replace @highlight @link type=none
    System.out.println("hello"); // @replace regex="???" replacement="xyz"
    System.out.println("hello"); // @replace regex="hello" replacement="$1"
  }
  // @end
}