// "Add static import for 'test.Calendar.getInstance'" "true-preview"
package test;

public class Foo {
    {
      GregorianCalendar.get<caret>Instance();
    }
}

class Calendar {
  public static final void getInstance() {}
}

class GregorianCalendar extends Calendar {
}