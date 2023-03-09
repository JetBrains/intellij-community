public class Region {
  // @start region=main
  public static void main(String[] args) {
    code: // @replace regex=code: replacement="..."
    System.out.println("Hello"); // @highlight substring=Hello
  }
  // @end
  
  // @start region=test
  public void test() {
    System.out.println("Region no markup");
  }
  // @end region=test
}