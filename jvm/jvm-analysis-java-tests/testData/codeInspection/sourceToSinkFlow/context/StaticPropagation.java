package com.example.sqlinjection;

import com.example.sqlinjection.utils.Utils;
import org.checkerframework.checker.tainting.qual.Tainted;
import org.checkerframework.checker.tainting.qual.Untainted;

public class StaticPropagation {
    public void invokeSink(@Tainted String param) {
      sink(<warning descr="Unsafe string is used as safe parameter">param</warning>);
      sink(Utils.safe(param));
      sink(Utils.encodeForHTML(param));
    }

  public static void sink(@Untainted String string) {}
}