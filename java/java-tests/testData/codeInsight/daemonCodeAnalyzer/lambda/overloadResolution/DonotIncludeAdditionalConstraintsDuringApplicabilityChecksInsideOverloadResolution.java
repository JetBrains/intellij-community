/*
 * Copyright (c) 2016. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

package usages;

import java.util.function.Function;

class Test {
  {
    valueOf(<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'char[]'">processFirst(x -> x)</error>);
  }

  public static <V> V processFirst(Function<Integer,V> f){
    return f.apply(1);
  }
  static void valueOf(Object o) {
    System.out.println(o);
  }
  static void valueOf(char[] c) {
    System.out.println(c);
  }
}