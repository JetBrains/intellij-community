public class Test {
    int method() {
        try {
            System.out.println("Text");
            return 0;
        } finally {
            <selection>System.out.println("!!!");
            return 1;</selection>
        }
    }
}