// "Add static import for 'test.Calendar.getInstance'" "true"
package test;

import static test.Calendar.getInstance;

public class Foo {
    {
      getInstance();
    }
}

class Calendar {
  public static final void getInstance() {}
}

class GregorianCalendar extends Calendar {
}