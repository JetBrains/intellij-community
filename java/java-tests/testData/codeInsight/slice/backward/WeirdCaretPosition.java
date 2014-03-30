class Foo {
    private static MyEnum staticMyEnum = MyEnum.<flown111>BAR;

    public static void main(String[] args) {
        produce();
    }

    static void produce() {
        produce(<flown11>staticMyEnum);
    }

    static void produce(MyEnum <flown1>myEnum) {
        System.out.println("myEnum: " + myEnum<caret>);
    }

    static void f() {
        staticMyEnum = MyEnum.<flown112>BAZ;
    }

    static void g() {
        staticMyEnum = MyEnum.<flown113>FOO;
    }

    enum MyEnum {
      <flown1131>FOO,
      <flown1111>BAR,
      <flown1121>BAZ
    }
}