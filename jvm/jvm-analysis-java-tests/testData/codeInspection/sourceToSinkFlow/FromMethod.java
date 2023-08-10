public class FromMethod {
  public void test(String untidy, String unknown) {
    sink("", <warning descr="Unsafe string is used as safe parameter">untidy</warning>);
    sink("", <warning descr="Unknown string is used as safe parameter">unknown</warning>);
    sink("", <warning descr="Unsafe string is used as safe parameter">unknown.toString()</warning>);
    sink("", unknown.trim());
  }

  private void sink(String any, String clean) {

  }
}
