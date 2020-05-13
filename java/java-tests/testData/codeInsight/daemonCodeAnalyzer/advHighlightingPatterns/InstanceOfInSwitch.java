class X {
  void test(Object obj, int x) {
    switch (x) {
      case 1:
        if (!(obj instanceof String s)) return;
        System.out.println(s.trim());
      case 2:
        if (!(obj instanceof Number s)) return;
        System.out.println(s.longValue());
      case 3:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
    }
  }
  
  void testWhile(Object obj, int x) {
    switch (x) {
      case 1:
        while(!(obj instanceof String s)) {
          obj = getNextObject();
        }
        System.out.println(s.trim());
      case 2:
        System.out.println(<error descr="Cannot resolve symbol 's'">s</error>.trim());
    }
  }
  
  native Object getNextObject();
}