package com.test;

import org.checkerframework.checker.tainting.qual.Untainted;

class Random {
  public int nextInt(int t) {
    return t;
  }
}

class SecureRandom extends Random {

}

public class SecureCustomInheritance {
  public void test() {
    int randNumber = new Random().nextInt(99);
    int secureRandNumber = new SecureRandom().nextInt(99);
    String secureRememberMeKey = Integer.toString(secureRandNumber);
    String rememberMeKey = Integer.toString(randNumber);
    sink(secureRememberMeKey);
    sink(<warning descr="Unsafe string is used as safe parameter">rememberMeKey</warning>);
  }

  private void sink(@Untainted String clean) {

  }
}
