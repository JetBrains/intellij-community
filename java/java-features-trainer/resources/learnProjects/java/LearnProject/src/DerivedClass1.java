
public class DerivedClass1 implements SomeInterface {
    @Override
    public void foo(FileStructureDemo demo) {
        demo.boo();
    }
}


class SecondLevelClassA extends DerivedClass1 {
    @Override
    public void foo(FileStructureDemo demo) {
        demo.foo();
    }
}


class SecondLevelClassB extends DerivedClass1 {
}
