// "Replace with enhanced 'switch' statement" "true"

class A {
  private void f()  {
    <caret>switch (1) {
      case 1:
        o.act(); //
        break;
      case 2:
        o.act(); //
        o.act();
        break;
    }
  }
}