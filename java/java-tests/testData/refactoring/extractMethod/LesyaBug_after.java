import java.io.IOException;
import java.io.OutputStream;

class A {
    {
        try {
            OutputStream out = null;

            newMethod(out);
        } catch(Throwable t) {
        }
    }

    private void newMethod(OutputStream out) throws IOException {
        try {
        } finally {
            out.close();
        }
    }
}