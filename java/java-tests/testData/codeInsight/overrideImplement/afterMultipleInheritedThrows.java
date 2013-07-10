import java.io.*;
interface A {
    void  close() throws Exception;
}

interface B {
    void close() throws IOException;
}

interface C<T extends Exception> {
    void close() throws T;
}

interface AB extends A, C, B {
    @Override
    void close() throws IOException;
}
