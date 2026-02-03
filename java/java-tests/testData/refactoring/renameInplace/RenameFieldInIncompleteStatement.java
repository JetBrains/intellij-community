class MyTest {

    String foo;

    {
        I i;

        foo<caret>
        i = MyTest::foo;
    }
}