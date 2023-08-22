import org.checkerframework.checker.tainting.qual.Untainted;

public class MethodPropagation {

  public void test1(String dirty, @Untainted String clean) {
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>); //warn
    sink(clean);
  }

  private String recursive(String dirty, @Untainted String clean) {
    if (clean == "") {
      String a = recursive(dirty, clean);
      sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">a</weak_warning>); //warn
      return recursive(dirty, clean);
    }
    return recursive(clean, clean);
  }


  public void test2(String dirty, @Untainted String clean) {
    sink(<warning descr="Unknown string is used as safe parameter">next(dirty)</warning>); //warn
    sink(next(clean));

    sink(<warning descr="Unknown string is used as safe parameter">nextPublic(dirty)</warning>); //warn
    sink(<warning descr="Unknown string is used as safe parameter">nextPublic(clean)</warning>); //warn (public)

    sink(alwaysClean(dirty));
    sink(alwaysClean(clean));

    sink(<warning descr="Unknown string is used as safe parameter">staticNext(dirty)</warning>); //warn
    sink(staticNext(clean));

    sink(next(next(clean)));
    sink(next(next(next(next(next(clean))))));
    sink(<warning descr="Unknown string is used as safe parameter">next(next(next(next(next(dirty)))))</warning>); //warn

    sink(alwaysClean(next(next(next(next(clean))))));
    sink(alwaysClean(next(next(next(next(dirty))))));

    sink(next(next(next(next(alwaysClean(clean))))));
    sink(next(next(next(next(alwaysClean(dirty))))));
    sink(next(alwaysClean(clean)));
    sink(next((alwaysClean(dirty))));

    String alwaysClean = alwaysClean(next(next(next(clean))));
    sink(alwaysClean);
    String alwaysClean2 = alwaysClean(next(next(next(dirty))));
    sink(alwaysClean2);
  }

  private String next(String next) {
    return next;
  }

  public static String staticNext(String next) {
    return next;
  }

  private String alwaysClean(String next) {
    return "next";
  }

  public String nextPublic(String next) {
    return next;
  }

  public static void sink(@Untainted String string) {

  }
}
