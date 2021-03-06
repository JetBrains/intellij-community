// "Replace with enhanced 'switch' statement" "true"

class A {
  private void f()  {
      switch (1) {
          case 1 -> o.act(); //
          case 2 -> {
              o.act(); //
              o.act();
          }
      }
  }
}