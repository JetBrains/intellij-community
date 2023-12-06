import org.checkerframework.checker.tainting.qual.Untainted;

class MethodPropagation {

  private String recursive(String dirty, @Untainted String clean) {
    if (clean == "") {
      String a = recursive(dirty,<error descr="Expression expected">)</error>;
      sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">a</weak_warning>);
      return recursive(clean, clean);
    }
    return recursive(clean, clean);
  }

  public static void sink(@Untainted String string) {

  }
}
