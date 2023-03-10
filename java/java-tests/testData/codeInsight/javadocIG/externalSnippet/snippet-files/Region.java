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
}