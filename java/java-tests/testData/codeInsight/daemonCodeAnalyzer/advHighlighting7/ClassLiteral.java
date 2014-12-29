import java.lang.reflect.*;

class Example {
    private void demo() {
        <error descr="Incompatible types. Found: 'java.lang.reflect.TypeVariable<java.lang.Class<capture<? extends Example>>>[]', required: 'java.lang.reflect.TypeVariable<java.lang.Class<? extends Example>>[]'">TypeVariable<Class<? extends Example>>[]  typeParameters  =  getClass().getTypeParameters();</error>
        Object typeParameters1  =  <error descr="Inconvertible types; cannot cast 'java.lang.reflect.TypeVariable<java.lang.Class<capture<? extends Example>>>[]' to 'java.lang.reflect.TypeVariable<java.lang.Class<? extends Example>>[]'">(TypeVariable<Class<? extends Example>>[]) getClass().getTypeParameters()</error>;
    }

    @Override
    public boolean equals(Object obj) {
      return getClass() == obj.getClass();
    }
}

class Foo<T> {

    public static Class<? extends Foo<?>> fFoo(final Foo<?> foo) {
        <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Foo>>', required: 'java.lang.Class<? extends Foo<?>>'">Class<? extends Foo<?>> fooClass = foo.getClass();</error>
        <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Foo>>', required: 'java.lang.Class<? extends Foo<?>>'">fooClass = foo.getClass()</error>;
        <error descr="Incompatible types. Found: 'java.lang.Class<? extends Foo>', required: 'java.lang.Class<? extends Foo<?>>'">return foo.getClass();</error>
    }

    public static Class<? extends Foo<? extends String>> fFoo1(final Foo<? extends String> foo) {
        <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Foo>>', required: 'java.lang.Class<? extends Foo<? extends java.lang.String>>'">Class<? extends Foo<? extends String>> fooClass = foo.getClass();</error>
        <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends Foo>>', required: 'java.lang.Class<? extends Foo<? extends java.lang.String>>'">fooClass = foo.getClass()</error>;
        <error descr="Incompatible types. Found: 'java.lang.Class<? extends Foo>', required: 'java.lang.Class<? extends Foo<? extends java.lang.String>>'">return foo.getClass();</error>
    }
}