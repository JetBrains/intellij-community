package com.siyeh.igtest.bugs.object_equality;

import java.util.*;

class Demo {
  public static void main(String[] args) {
    final int a = 0;
    final int b = new Integer("0");

    if (a != b) {
      System.out.println("b: " + b);
    }

    final String[] value = {"1", "2"};
    final int c = new Integer(value[0]);

    if (a != c) {
      System.out.println("aaa");
    }
  }
}