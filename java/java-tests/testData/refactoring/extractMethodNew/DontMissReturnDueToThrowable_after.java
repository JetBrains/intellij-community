import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class Test {
    void test(String name) throws IOException {
        String s = newMethod();

        System.out.println(s);
    }

    private @NotNull String newMethod() throws IOException {
        String s = "result";
        new Test().withError();
        return s;
    }

    void withError() throws IOException {
    }
}