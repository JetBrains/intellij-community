import java.io.OutputStream;

class A {
    {
        try {
            OutputStream out = null;

            <selection>try {
            } finally {
                out.close();
            }</selection>
        } catch(Throwable t) {
        }
    }
}