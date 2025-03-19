package com.siyeh.igtest.bugs.result_of_object_allocation_ignored;

import java.util.function.*;

public class ResultOfObjectAllocationIgnored {

    private ResultOfObjectAllocationIgnored() {
        super();
    }

    public static void foo() {
        new <warning descr="Result of 'new Integer()' is ignored">Integer</warning>(3);
        new java.util.ArrayList();
    }

    void boom() {
        new <warning descr="Result of 'new Comparable<String>()' is ignored">Comparable<String></warning>() {

            public int compareTo(String o) {
                return 0;
            }
        };
    }

    Throwable switchExpression(int i) {
        return switch(i) {
            default -> new Throwable();
        };
    }
    
    void methodRef() {
      Runnable simple = <warning descr="Object allocated inside 'Object::new' is discarded">Object::new</warning>;
      Runnable impliciCtor = <warning descr="Object allocated inside 'Foo::new' is discarded">Foo::new</warning>;
      Runnable qualified = <warning descr="Object allocated inside 'java.lang.Object::new' is discarded">java.lang.Object::new</warning>;
      Runnable ignored = java.util.ArrayList::new;
      IntConsumer primitiveArr = <warning descr="Object allocated inside 'int[]::new' is discarded">int[]::new</warning>;
      IntConsumer objectArr = <warning descr="Object allocated inside 'Object[]::new' is discarded">Object[]::new</warning>;
    }
    
    class Foo {}
}