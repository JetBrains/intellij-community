public class SomeClass {

    {
        java.util.List<Unresolved> l;
        Unresolved[] a = l.toArray((Unresolved[]) <caret>expr);
    }

}
