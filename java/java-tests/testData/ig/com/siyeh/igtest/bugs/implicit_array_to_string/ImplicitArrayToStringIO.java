package com.siyeh.igtest.bugs.implicit_array_to_string;

import java.io.PrintWriter;
import java.util.Formatter;

public class ImplicitArrayToStringIO {

    void foo() {
      IO.println("T");
      IO.print("T");

      IO.println(<warning descr="Implicit call to 'toString()' on array 'new char[]{'1', '2'}'">new char[]{'1', '2'}</warning>);
      IO.print(<warning descr="Implicit call to 'toString()' on array 'new char[]{'1', '2'}'">new char[]{'1', '2'}</warning>);

      IO.println(<warning descr="Implicit call to 'toString()' on array 'new byte[]{'1', '2'}'">new byte[]{'1', '2'}</warning>);
      IO.print(<warning descr="Implicit call to 'toString()' on array 'new byte[]{'1', '2'}'">new byte[]{'1', '2'}</warning>);
    }
}
