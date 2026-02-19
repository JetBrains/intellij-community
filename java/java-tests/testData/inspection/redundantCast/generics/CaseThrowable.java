public class RedundantCast{
    void m(IOEx e) throws IOEx {
        throw <error descr="Inconvertible types; cannot cast 'RedundantCast.IOEx' to 'RedundantCast.FileNotFoundEx'">(<warning descr="Casting 'e' to 'FileNotFoundEx' is redundant">FileNotFoundEx</warning>)e</error>;
    }

    void foo(RuntimeException e, boolean f) {
        if (f) {
            throw (<warning descr="Casting '(Object)e' to 'Ex' is redundant">Ex</warning>)(<warning descr="Casting 'e' to 'Object' is redundant">Object</warning>)e;
        }
        else if (!f && false) {
            throw <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.Throwable'">(<warning descr="Casting 'e' to 'Object' is redundant">Object</warning>)e;</error>
        }
        throw (<warning descr="Casting '(Ex)e' to 'Ex' is redundant">Ex</warning>)(<warning descr="Casting 'e' to 'Ex' is redundant">Ex</warning>)e;
    }

    void bar(E2 e) throws E1 {
        throw (<warning descr="Casting 'e' to 'E1' is redundant">E1</warning>)e;
    }
    
    void bar(E1 e) throws E2 {
        if (true) {
            try {
                throw (E2)e;
            }
            catch (E2 e2) {}
        }
        throw (E2)e;
    }
    
    static class Ex extends RuntimeException {}
    static class IOEx extends Exception {}
    static class FileNotFoundEx extends Exception {}
    
    static class E1 extends Exception {}
    static class E2 extends E1 {}
}
