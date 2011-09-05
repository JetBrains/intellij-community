// "Add on demand static import for 'test.GregorianCalendar'" "true"
package test;

public class Foo {
    {
      <caret>GregorianCalendar.getInstance();
    }
}

class Calendar {
  public static final void getInstance() {}
}

class GregorianCalendar extends Calendar {
}