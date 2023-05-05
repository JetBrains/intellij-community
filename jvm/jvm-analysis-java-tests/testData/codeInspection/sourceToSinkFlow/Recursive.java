import org.checkerframework.checker.tainting.qual.Untainted;

class MethodPropagation {

  private String recursive(String dirty, @Untainted String clean) {
    if (clean == "") {
      String a = recursive(dirty,<error descr="Expression expected">)</error>;
      sink(<warning descr="Unknown string is used as safe parameter">a</warning>);
      return recursive(clean, clean);
    }
    return recursive(clean, clean);
  }

  public static void sink(@Untainted String string) {

  }
}
