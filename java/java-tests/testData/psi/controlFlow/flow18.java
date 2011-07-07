// LocalsOrMyInstanceFieldsControlFlowPolicy
import java.io.IOException;
import java.io.OutputStream;
public class Outer {
    void f(OutputStream writer) {  <caret>
        try {
            try {
                throw new IOException();
            } finally {
                writer.close();
            }
        }
        catch (IOException e) {
          e.hashCode();
        }
    }
}
