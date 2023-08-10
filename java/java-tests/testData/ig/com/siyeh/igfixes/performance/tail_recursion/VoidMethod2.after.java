class Test {
  void foo(int n, int acc) {
      while (true) {
          if (n % 2 == 0) {
              System.out.println(acc);
          } else if (n % 3 == 0) {
              foo(n - 1, acc + n);
          } else if (n % 5 == 0) {
              acc = acc + n;
              n = n - 1;
              continue;
          } else if (n % 7 == 0) {
              System.out.println(acc);
              return;
          }
          acc = acc + n;
          n = n - 1;
      }
  }
}