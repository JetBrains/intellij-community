class Test1 {

    interface VoidReturnType {
        void foo();
    }
    {
        VoidReturnType aI = () -> System.out.println();
        VoidReturnType aI1 = () -> {System.out.println();};
        VoidReturnType aI2 = <error descr="Unexpected return value">() -> {return 1;}</error>;
        VoidReturnType aI3 = <error descr="Incompatible return type int in lambda expression">() -> 1</error>;
        VoidReturnType aI4 = () -> {return;};
    }
}

class Test2 {
    interface IntReturnType {
        int foo();
    }
    {
        IntReturnType aI = <error descr="Incompatible return type void in lambda expression">() -> System.out.println()</error>;
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
        XReturnType<Object> aI = <error descr="Incompatible return type void in lambda expression">() -> System.out.println()</error>;
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
        YXReturnType<Object> aI = <error descr="Incompatible return type void in lambda expression">() -> System.out.println()</error>;
        YXReturnType<Object> aI1 = () -> {System.out.println();<error descr="Missing return statement">}</error>;
        YXReturnType<Object> aI2 = <error descr="Incompatible return type int in lambda expression">() -> {return 1;}</error>;
        YXReturnType<Object> aI3 = <error descr="Incompatible return type int in lambda expression">() -> 1</error>;
        YXReturnType<Object> aI4 = () -> new Y<Object>(){};
        YXReturnType<Object> aIDiamond = () -> new Y<>();
        
    }
}

