class MainTest {
  void check(boolean flag) {
    Object o1 = <flown111111>"foo";
    Object o2 = <flown112111>"bar";
    Object o3 = "baz";
    Object o4 = "qux";
    Object res1 = <flown1>test(flag, <flown11111>o1, <flown11211>o2);
    Object res2 = test(flag, o3, o4);
    System.out.println(<caret>res1);
  }

  static Object test(boolean flag, Object <flown1111>x, Object <flown1121>y) {
    return <flown11>flag ? <flown111>x : <flown112>y;
  }
}