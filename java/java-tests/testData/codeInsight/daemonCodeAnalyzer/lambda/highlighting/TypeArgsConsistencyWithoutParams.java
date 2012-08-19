import java.util.*;
class Test4 {
   interface I<K> {
       List<K> foo();
   }
    
    static <T> void bar(I<T> i){}
    
    {
        bar(() -> null);
    }
}

class Test5 {
    interface I<K> {
        void foo(K k);
    }

    static <T> void bar(I<T> i){}

    {
        bar<error descr="'bar(Test5.I<T>)' in 'Test5' cannot be applied to '(<lambda expression>)'">(() -> null)</error>;
    }
}
class Test6 {
    interface I<K> {
        void foo();
    }

    static <T> void bar(I<T> i){}

    {
        bar<error descr="'bar(Test6.I<java.lang.Object>)' in 'Test6' cannot be applied to '(<lambda expression>)'">(() -> null)</error>;
        bar(() -> {});
    }
}