package com.siyeh.igtest.portability.system_exit;



class SystemExit {

  void foo() {
    System.<warning descr="Call to 'System.exit()' is non-portable">exit</warning>(0);
  }

  public static void main(String[] args) {
    System.exit(1);
  }

  public static void other(String[] args) {
    System.<warning descr="Call to 'System.exit()' is non-portable">exit</warning>(2);
  }

  void third() {
    Runtime.getRuntime().<warning descr="Call to 'Runtime.halt()' is non-portable">halt</warning>(1);
  }
}