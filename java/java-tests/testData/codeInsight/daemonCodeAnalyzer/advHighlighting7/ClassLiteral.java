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
