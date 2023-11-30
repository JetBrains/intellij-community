class C {
  void foo() {
    try {
      bar();
    }
    catch (NullPointerException | ArithmeticException e) {
      //skip
    }
    catch /*skip*/ (IllegalStateException e) {
      //
    }
    catch (/*skip*/ IllegalArgumentException e) {
      //
    }
    catch (IndexOutOfBoundsException /*skip*/ e) {
      //
    }
    catch (IllegalMonitorStateException e) /*skip*/ {
      //
    }
    catch (ClassCastException e) { }
    catch // other
    (RuntimeException e) {
      //skip
    }
  }

  void bar(){}
}