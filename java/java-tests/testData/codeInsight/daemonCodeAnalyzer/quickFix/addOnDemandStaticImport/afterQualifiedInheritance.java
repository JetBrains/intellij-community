// "Add on-demand static import for 'test.GregorianCalendar'" "true-preview"
package test;

import static test.GregorianCalendar.*;

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