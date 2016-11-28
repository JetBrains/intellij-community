class NestedTryFinallyInEndlessLoop {
  public void boo() {
    int n;
    while (true) {
      try {
        try {
          <warning descr="The value 1 assigned to 'n' is never used">n</warning> = 1;
        } finally {
          <warning descr="The value 2 assigned to 'n' is never used">n</warning> = 2;
        }
      } catch (Throwable t) {
        System.out.println(t);
      }
    }
  }

  public void foo() {
    int n;
    while (true) {
      try {
        try {
          <warning descr="The value 1 assigned to 'n' is never used">n</warning> = 1;
        } finally {
          try {
            <warning descr="The value 2 assigned to 'n' is never used">n</warning> = 2;
          } finally {
            <warning descr="The value 3 assigned to 'n' is never used">n</warning> = 3;
          }
        }
      } catch (Throwable t) {
        System.out.println(t);
      }
    }
  }

  public void bar() {
    int n;
    while (true) {
      try {
        try {
          <warning descr="The value 1 assigned to 'n' is never used">n</warning> = 1;
        } finally {
          try {
            <warning descr="The value 2 assigned to 'n' is never used">n</warning> = 2;
          } finally {
            try {
              <warning descr="The value 3 assigned to 'n' is never used">n</warning> = 3;
            } finally {
              <warning descr="The value 4 assigned to 'n' is never used">n</warning> = 4;
            }
          }
        }
      } catch (Throwable t) {
        System.out.println(t);
      }
    }
  }

  public void fiz() {
    int n;
    while (true) {
      try {
        try {
          <warning descr="The value 1 assigned to 'n' is never used">n</warning> = 1;
        } finally {
          try {
            <warning descr="The value 2 assigned to 'n' is never used">n</warning> = 2;
          } finally {
            try {
              <warning descr="The value 3 assigned to 'n' is never used">n</warning> = 3;
            } finally {
              try {
                <warning descr="The value 4 assigned to 'n' is never used">n</warning> = 4;
              } finally {
                <warning descr="The value 5 assigned to 'n' is never used">n</warning> = 5;
              }
            }
          }
        }
      } catch (Throwable t) {
        System.out.println(t);
      }
    }
  }

  public void baz() {
    int n;
    while (true) {
      try {
        try {
          <warning descr="The value 1 assigned to 'n' is never used">n</warning> = 1;
        } finally {
          try {
            <warning descr="The value 2 assigned to 'n' is never used">n</warning> = 2;
          } finally {
            try {
              <warning descr="The value 3 assigned to 'n' is never used">n</warning> = 3;
            } finally {
              try {
                <warning descr="The value 4 assigned to 'n' is never used">n</warning> = 4;
              } finally {
                try {
                  <warning descr="The value 5 assigned to 'n' is never used">n</warning> = 5;
                } finally {
                  <warning descr="The value 6 assigned to 'n' is never used">n</warning> = 6;
                }
              }
            }
          }
        }
      } catch (Throwable t) {
        System.out.println(t);
      }
    }
  }
}