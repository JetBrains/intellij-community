package com.siyeh.igtest.initialization.this_escaped_in_constructor;

import java.util.ArrayList;
import java.util.List;

public class ThisEscapedInConstructorInspection{
    private boolean foo = Testing.foo(<warning descr="Escape of 'ThisEscapedInConstructorInspection.this' during object construction">ThisEscapedInConstructorInspection.this</warning>, <warning descr="Escape of 'this' during object construction">this</warning>);

    {
        final NoEscape escape = new NoEscape(this); // safe
        new NoEscape(null).field = this; // safe
        System.out.println(<warning descr="Escape of 'this' during object construction">this</warning>);
    }

    public ThisEscapedInConstructorInspection(){
        super();
        final List list = new ArrayList(3);
        list.add(<warning descr="Escape of 'this' during object construction">this</warning>); //escape
        new Testing().boom = <warning descr="Escape of 'this' during object construction">this</warning>; // escape
        Runnable r = () -> System.out.println(this);
    }

    private class NoEscape {
        Object field;

        NoEscape(Object o) {
        }
    }
}
class Testing
{
  Object boom;

  public static boolean  foo(Object val)
  {
    return true;
  }

  public static boolean foo(Object o1, Object o2) {
    return false;
  }
}
