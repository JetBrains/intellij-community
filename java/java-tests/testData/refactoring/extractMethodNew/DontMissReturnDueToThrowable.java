import java.io.IOException;

class Test {
    void test(String name) throws IOException {
        <selection>String s = "result";
        new ReturnIssues().withError();</selection>

        System.out.println(s);
    }

    void withError() throws IOException {
    }
}