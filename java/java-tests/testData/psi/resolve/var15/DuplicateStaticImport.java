import static java.lang.System.out;
import static java.lang.System.out;

class DuplicateStaticImport {
    public static void main(String[] args) {
        <caret>out.println(""); // cannot resolve symbol 'out'
    }
}