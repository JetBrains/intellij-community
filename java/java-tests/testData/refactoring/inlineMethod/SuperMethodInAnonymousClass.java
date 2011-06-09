class A {
    public void f() {
      g();
    }

    public void g() {
    }
  }

  class B extends A {
    public void g() {

    }
    public void h() {
      new Runnable() {

        @Override
        public void run() {
          f<caret>();
        }
      }.run();
    }
  }
