public class Test {
    interface I {
        void foo();
    }

    public static void main(String[] args){
        I i = <error descr="Cannot resolve symbol 'UnknownClass'">UnknownClass</error>::wait;
    }
}