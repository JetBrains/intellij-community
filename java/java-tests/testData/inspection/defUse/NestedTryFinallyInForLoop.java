class Demo {
  private static class A {
    private void a() { }
    private void b() { }
    private void c() { }
    private F[] f() { return new F[0]; }
    private String g() { return ""; }
    private H[] h() { return new H[0]; }
    private void i() { }
  }

  private static class F {
    private void f() { }
  }

  private static class H {
    private void h() { }
  }

  public void f() {
    try {
      A b = new A();
      try {
        A a = new A();
        try {
          boolean c = false;
          try {
            a.a();
            c = true;
          } finally {
            if (!c) {
              o(null);
            }
          }
          for (H h : a.h()) {
            boolean d = false;
            try {
              try {
                h.h();
                d = true;
              } catch (Exception e) {
                o(e);
              } finally {
                if (d) {
                  c = false;
                  try {
                    a.a();
                    c = true;
                  } finally {
                    if (!c) {
                      a.c();
                    }
                  }
                  for (F f : a.f()) {
                    try {
                      f.f();
                    } catch (RuntimeException e) {
                      o(e);
                    }
                  }
                  c = false;
                  try {
                    a.a();
                    c = true;
                  } finally {
                    if (!c) {
                      a.c();
                    }
                  }
                } else {
                  c = false;
                  try {
                    a.b();
                    c = true;
                  } finally {
                    if (!c) {
                      a.c();
                    }
                  }
                  a.i();
                  c = false;
                  try {
                    a.a();
                    c = true;
                  } finally {
                    if (!c) {
                      a.c();
                    }
                  }
                }
              }
            } catch (Exception e) {
              o(e);
            } finally {
              c = false;
              try {
                if (d)
                  b.a();
                else
                  b.b();
                c = true;
              } finally {
                if (!c) {
                  b.c();
                }
              }
            }
          }
          boolean d = false;
          b.i();
          try {
            a.i();
            String g = <warning descr="Variable 'g' initializer 'null' is redundant">null</warning>;
            try {
              g = a.g();
              d = true;
              o(g);
            } catch (Exception e) {
              o(e);
            } finally {
              if (d) {
                c = false;
                try {
                  a.a();
                  c = true;
                } finally {
                  if (!c) {
                    a.c();
                  }
                }
                a.i();
                for (F f : a.f()) {
                  try {
                    f.f();
                  } catch (RuntimeException e) {
                    o(e);
                  }
                }
                c = false;
                try {
                  a.a();
                  c = true;
                } finally {
                  if (!c) {
                    a.c();
                  }
                }
              } else {
                c = false;
                try {
                  a.b();
                  c = true;
                } finally {
                  if (!c) {
                    a.c();
                  }
                }
              }
            }
          } finally {
            c = false;
            try {
              if (d)
                b.a();
              else
                b.b();
              c = true;
            } finally {
              if (!c) {
                b.c();
              }
            }
          }
        } finally {
          a.c();
        }
      } finally {
        b.c();
      }
    } catch (Exception e) {
      o(e);
    }
  }

  private void o(Object o) { }
}
