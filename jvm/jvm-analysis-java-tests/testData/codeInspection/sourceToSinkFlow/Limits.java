import org.checkerframework.checker.tainting.qual.Untainted;

class Limit {

  public final static String fromAnotherFile = Limit2.fromAnotherFile;
  public final static String fromAnotherFile2 = Limit2.fromAnotherFile2;
  public final static String fromAnotherFile3 = Limit2.fromAnotherFile3;
  public final static String fromAnotherFile4 = Limit2.fromAnotherFile4;
  public final static String fromAnotherFile5 = new Limit2().fromAnotherFile5;
  public final static String fromAnotherFile6 = new Limit2().fromAnotherFile6;

  public static void test(@Untainted String clear, String dirty) {
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>); //warn
    sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">next(next(next(next(next(next(next(next(next(next(next(next(next(next(next(dirty)))))))))))))))</weak_warning>); //warn complex
    sink(<warning descr="Unknown string is used as safe parameter">next(next(next(next(next(dirty)))))</warning>); //warn
    sink(next(next(next(next(next(clear))))));
    sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear)))))))</weak_warning>);
    sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(clear))))))) +
         next(next(next(next(next(next(next(dirty)))))))</weak_warning>); //warn
    sink(fromAnotherFile);
    sink(fromAnotherFile2); //not warn, because static final files are considered as safe
    sink(fromAnotherFile3); //not warn, because static final are considered as safe
    sink(<warning descr="Unknown string is used as safe parameter">fromAnotherFile4</warning>); //warn
    sink(<warning descr="Unknown string is used as safe parameter">fromAnotherFile5</warning>); //warn because ide doesn't process other files
    sink(<warning descr="Unknown string is used as safe parameter">fromAnotherFile6</warning>); //warn
    String cleanLongString = "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             clear +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh";
    sink(cleanLongString);
    sink(<warning descr="Unknown string is used as safe parameter">cleanLongString + dirty</warning>); //warn
    String dirtyLongString = "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             dirty +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             clear +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh" +
                             "sdafjhasdfkhaskjdfh";
    sink(<warning descr="Unknown string is used as safe parameter">dirtyLongString</warning>); //warn

    String a1 = clear + 1 + clear + clear + clear + clear + clear + clear + clear;
    String a2 = a1 + 1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1;
    String a3 = a2 + 1 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2;
    String a4 = a3 + 1 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3;
    String a5 = a4 + 1 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4;
    String a6 = a5 + 1 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5;
    String a7 = a6 + 1 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6;
    String a8 = a7 + 1 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7;
    String a9 = a8 + 1 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8;
    String a10 = a9 + 1 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9 + a9;
    sink(<weak_warning descr="Too complex to check that the string is safe in a safe context">a10</weak_warning>); //warn
    sink(a2);
  }

  public static String next(String next) {
    return next;
  }

  public static void sink(@Untainted String string) {

  }
}
