package com.siyeh.igtest.naming;

public class ConfusingMainMethod {
  public static void <warning descr="Method 'main()' does not have signature 'public static void main(String[])'">main</warning>() {}
}
class One {
  public void <warning descr="Method 'main()' does not have signature 'public static void main(String[])'">main</warning>(String[] args) {}
}
class Two {
  void <warning descr="Method 'main()' does not have signature 'public static void main(String[])'">main</warning>(String[][] args) {}
}
class Three {
  public static void main(String[] args) {}

  void foo() {
    new Object() {
      public static void <warning descr="Method 'main()' can't be run because the containing class does not have a fully qualified name">main</warning>(String[] args) {}
    };
    class Local {
      public static void <warning descr="Method 'main()' can't be run because the containing class does not have a fully qualified name">main</warning>(String[] args) {}
    }
  }
}
