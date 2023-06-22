import org.checkerframework.checker.tainting.qual.Untainted;

class FieldsCheck {
  final String constant = "1";
  private String clean = "1";
  private String notClean = "1";

  final String finalAppliedFromConstructor;
  private String appliedFromConstructor;
  private String clean2;

  public FieldsCheck(String finalAppliedFromConstructor, String appliedFromConstructor) {
    this.finalAppliedFromConstructor = finalAppliedFromConstructor;
    this.appliedFromConstructor = appliedFromConstructor;
    clean2 = "2";
  }

  public void setNotClean(String notClean) {
    this.notClean = notClean;
  }

  public void test() {
    sink(constant);
    sink(clean);
    sink(clean2);
    sink(<warning descr="Unknown string is used as safe parameter">notClean</warning>); //warn
    sink(<warning descr="Unknown string is used as safe parameter">finalAppliedFromConstructor</warning>); //warn
    sink(<warning descr="Unknown string is used as safe parameter">appliedFromConstructor</warning>); //warn
  }

  void sink(@Untainted String s) {
  }

}
