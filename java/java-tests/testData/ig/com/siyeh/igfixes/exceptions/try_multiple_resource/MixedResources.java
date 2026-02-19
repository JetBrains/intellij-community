import java.io.*;
class C {
    void m() throws Exception {
        Reader r2 = new StringReader();
        try (Reader r1 = new StringReader()<caret>; r2) {
            System.out.println(r1 + ", " + r2);
        }
    }
}