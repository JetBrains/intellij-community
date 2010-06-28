class Foo {
    private static MyEnum staticMyEnum = MyEnum.<flown11>BAR;

    public static void main(String[] args) {
        produce();
    }

    static void produce() {
        produce(<flown1>staticMyEnum);
    }

    static void produce(MyEnum myEnum) {
        System.out.println("myEnum: " + myEnum<caret>);
    }

    static void f() {
        staticMyEnum = MyEnum.<flown12>BAZ;
    }

    static void g() {
        staticMyEnum = MyEnum.<flown13>FOO;
    }

    enum MyEnum {
        FOO,
        BAR,
        BAZ
    }
}