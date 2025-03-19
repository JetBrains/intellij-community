package com.siyeh.igtest.classlayout.public_constructor_in_non_public_class;

record Rec() {
  <warning descr="Constructor is declared 'public' in non-public class 'Rec'">public</warning> Rec {}
  <warning descr="Constructor is declared 'public' in non-public class 'Rec'">public</warning> Rec(int x) {
    this();
    System.out.println(x);
  }
}
record Rec2(int x) {
  <warning descr="Constructor is declared 'public' in non-public class 'Rec2'">public</warning> Rec2() {
    this(0);
  }
  <warning descr="Constructor is declared 'public' in non-public class 'Rec2'">public</warning> Rec2(int x) {
    System.out.println(x);
    this.x = x;
  }
}