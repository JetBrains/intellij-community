// "Convert to record class" "false"
class Point2<caret> {
  private final double x;
  private final double y;
  
  Point2(double x) {
    // Cannot be converted with semantics preserved because JLS § 8.10.4 says:
    // The body of every non-canonical constructor in a record declaration must start with an alternate constructor invocation
    System.out.println("Before fields are assigned");
    this.x = x;
    this.y = 0;
    System.out.println("After fields are assigned");
  }

  Point2(double x, double y) {
    System.out.println("Before fields are assigned");
    this.x = x;
    this.y = y;
    System.out.println("After fields are assigned");
  }
}
