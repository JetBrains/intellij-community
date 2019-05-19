public class RedundantCast{
    void m(IOEx e) throws IOEx {
        throw <error descr="Inconvertible types; cannot cast 'RedundantCast.IOEx' to 'RedundantCast.FileNotFoundEx'">(<warning descr="Casting 'e' to 'FileNotFoundEx' is redundant">FileNotFoundEx</warning>)e</error>;
    }

    void foo(RuntimeException e) {
        throw (<warning descr="Casting 'e' to 'Ex' is redundant">Ex</warning>)e;
    }

    static class Ex extends RuntimeException {}
    static class IOEx extends Exception {}
    static class FileNotFoundEx extends Exception {}
}
