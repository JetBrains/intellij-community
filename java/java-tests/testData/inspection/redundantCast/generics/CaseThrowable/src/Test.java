public class RedundantCast{
    void m(IOEx e) throws IOEx {
        throw (FileNotFoundEx)e;
    }

    void foo(RuntimeException e) {
        throw (Ex)e;
    }

    static class Ex extends RuntimeException {}
    static class IOEx extends Exception {}
    static class FileNotFoundEx extends Exception {}
}
