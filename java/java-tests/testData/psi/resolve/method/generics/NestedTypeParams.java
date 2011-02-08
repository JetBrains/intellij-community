interface TestInterface {

    <E extends Enum<E>> void doStuff(E thing);
}

class  TestImpl implements TestInterface {
    @Override
    public <I extends Enum<I>> void doStuff(I thing){

    }
}

enum TestEnum {
    THING
}

class Testx {
    public void doTest(){
        TestImpl impl = new TestImpl();
        impl.<ref>doStuff(TestEnum.THING);
    }
}
