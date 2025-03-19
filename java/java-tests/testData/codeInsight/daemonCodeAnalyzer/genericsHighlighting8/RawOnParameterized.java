class GenericOuter<T> {
    public class GenericInner<U> {
    }

    public static class StaticGenericInner<U> {
    }

    public class SimpleInner {
    }
}

class OuterClient {
    public void context() {
        <error descr="Improperly formed type: 'GenericInner' needs type arguments because its qualifier has type arguments">GenericOuter<String>.GenericInner</error> v1 = null;
        GenericOuter.GenericInner<error descr="Type arguments given on a raw type"><String></error> v2 = null;
        GenericOuter.GenericInner v3 = null; 
        GenericOuter<String>.GenericInner<String> v4 = null;

        GenericOuter<error descr="Type arguments are not allowed here because class 'GenericOuter.StaticGenericInner' is static"><String></error>.StaticGenericInner sv1 = null;
        GenericOuter.StaticGenericInner<String> sv2 = null;
        GenericOuter.StaticGenericInner sv3 = null;
        GenericOuter<error descr="Type arguments are not allowed here because class 'GenericOuter.StaticGenericInner' is static"><String></error>.StaticGenericInner<String> sv4 = null;

        GenericOuter<String>.SimpleInner iv1 = null;
        GenericOuter.SimpleInner<error descr="Type arguments given on a raw type"><String></error> iv2 = null;
        GenericOuter.SimpleInner iv3 = null;
        GenericOuter<String>.SimpleInner<error descr="Type 'GenericOuter.SimpleInner' does not have type parameters"><String></error> iv4 = null;
    }
}
