import java.io.IOException;

class Test {
    void test(String name) throws IOException {
        <selection>String s = "result";
        new Test().withError();</selection>

        System.out.println(s);
    }

    void withError() throws IOException {
    }
}