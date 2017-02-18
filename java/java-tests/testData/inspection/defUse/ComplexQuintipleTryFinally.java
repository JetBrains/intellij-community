class Stress {
  int foo() {
    int a = 0, b = 0, c = 0, d = 0, e = 0;
    A:
    try {
      a++;
    }
    finally {
      System.out.println(a);
      B:
      try {
        b++;
      }
      finally {
        System.out.println(b);
        C:
        try {
          c++;
        }
        finally {
          System.out.println(c);
          D:
          try {
            d++;
          }
          finally {
            System.out.println(d);
            E:
            try {
              <warning descr="The value changed at 'e++' is never used">e++</warning>;
              if (a == 0) break A;
              if (b == 0) break B;
              if (c == 0) break C;
              if (d == 0) break D;
              return a + b + c + d;
            }
            finally {
              System.out.println(a + " " + b + " " + c + " " + d);
              if (a + b + c + d == 0) break E;
            }
          }
        }
      }
    }
    return 0;
  }
}