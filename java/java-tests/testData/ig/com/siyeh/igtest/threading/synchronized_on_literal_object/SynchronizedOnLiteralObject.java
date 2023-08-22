package com.siyeh.igtest.threading.synchronized_on_literal_object;

import java.util.concurrent.atomic.AtomicInteger;

class SynchronizedOnLiteralObject {

  private Integer myInteger = (Integer)1;
  private Character c = 'a';

  void foo() {
    synchronized (<warning descr="Synchronization on Integer 'myInteger' which is initialized by a literal">myInteger</warning>) {}
    synchronized (<warning descr="Synchronization on String literal '\"asdf\"'">"asdf"</warning>) {}
    synchronized (<warning descr="Synchronization on Boolean literal '(Boolean) true'">(Boolean) true</warning>) {}
    synchronized (<warning descr="Synchronization on Character 'c' which is initialized by a literal">c</warning>) {}
  }

  private AtomicInteger count = new AtomicInteger(1);

  void up() {
    synchronized (count) {
      System.out.println();
    }
  }
}