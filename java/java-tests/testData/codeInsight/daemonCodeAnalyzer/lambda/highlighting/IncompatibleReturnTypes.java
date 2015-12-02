class Test1 {

    interface VoidReturnType {
        void foo();
    }
    {
        VoidReturnType aI = () -> System.out.println();
        VoidReturnType aI1 = () -> {System.out.println();};
        VoidReturnType aI2 = () -> {return <error descr="Unexpected return value">1</error>;};
        VoidReturnType aI3 = () -> <error descr="Bad return type in lambda expression: int cannot be converted to void">1</error>;
        VoidReturnType aI4 = () -> {return;};
    }
}

class Test2 {
    interface IntReturnType {
        int foo();
    }
    {
        <error descr="Incompatible types. Found: '<lambda expression>', required: 'Test2.IntReturnType'">IntReturnType aI = () -> System.out.println();</error>
        IntReturnType aI1 = () -> {System.out.println();<error descr="Missing return statement">}</error>;
        IntReturnType aI2 = () -> {return 1;};
        IntReturnType aI3 = () -> 1;
    }
}


class Test3 {

    interface XReturnType<X> {
        X foo();
    }
    {
        <error descr="Incompatible types. Found: '<lambda expression>', required: 'Test3.XReturnType<java.lang.Object>'">XReturnType<Object> aI = () -> System.out.println();</error>
        XReturnType<Object> aI1 = () -> {System.out.println();<error descr="Missing return statement">}</error>;
        XReturnType<Object> aI2 = () -> {return 1;};
        XReturnType<Object> aI3 = () -> 1;
        XReturnType<Object> aI4 = () -> {<error descr="Missing return statement">}</error>;
    }
}

class Test4 {
    class Y<T>{}
    
    interface YXReturnType<X> {
        Y<X> foo();
    }

    {
        <error descr="Incompatible types. Found: '<lambda expression>', required: 'Test4.YXReturnType<java.lang.Object>'">YXReturnType<Object> aI = () -> System.out.println();</error>
        YXReturnType<Object> aI1 = () -> {System.out.println();<error descr="Missing return statement">}</error>;
        <error descr="Incompatible types. Found: '<lambda expression>', required: 'Test4.YXReturnType<java.lang.Object>'">YXReturnType<Object> aI2 = () -> {return 1;};</error>
        <error descr="Incompatible types. Found: '<lambda expression>', required: 'Test4.YXReturnType<java.lang.Object>'">YXReturnType<Object> aI3 = () -> 1;</error>
        YXReturnType<Object> aI4 = () -> new Y<Object>(){};
        YXReturnType<Object> aIDiamond = () -> new Y<>();
        
    }
}

