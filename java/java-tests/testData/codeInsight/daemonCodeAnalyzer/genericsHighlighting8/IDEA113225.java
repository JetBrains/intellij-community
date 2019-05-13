import java.io.Serializable;

class Test {
    public <T extends Serializable> void foo(byte[] data) {
        T foo = (T)  data;
    }

    public <T extends Serializable & Runnable> void bar(byte[] data) {
        T bar = <error descr="Inconvertible types; cannot cast 'byte[]' to 'T'">(T)  data</error>;
    }
}
