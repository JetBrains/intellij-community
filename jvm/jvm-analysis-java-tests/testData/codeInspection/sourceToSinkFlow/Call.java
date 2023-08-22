import org.checkerframework.checker.tainting.qual.Untainted;

class CallsCheck {

  public void testCall(String dirty, @Untainted String clean) {
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>); //warn
    sink("");
    sink(cleanMethod());
    sink(<warning descr="Unknown string is used as safe parameter">publicMethod()</warning>); //warn
    sink(publicFinalMethod());
    sink(<warning descr="Unknown string is used as safe parameter">privateDirty(dirty)</warning>); //warn
    sink(<warning descr="Unknown string is used as safe parameter">dirty.toLowerCase()</warning>); //warn
    sink(dirty.getClass().getName());
    sink(<warning descr="Unknown string is used as safe parameter">dirty.replace("1", "2")</warning>); //warn
    sink(clean);
    sink(<warning descr="Unknown string is used as safe parameter">clean.replace("1", dirty)</warning>); //warn
  }

  private String privateDirty(String dirty) {
    return dirty;
  }

  public String publicMethod() {
    return "1";
  }
  public final String publicFinalMethod() {
    return "1";
  }

  private String cleanMethod() {
    return "null";
  }

  public void sink(@Untainted String clean) {

  }
}
