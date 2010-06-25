// "Remove variable 'o'" "true"
import java.io.*;

class a {
    private int run() {
        Object <caret>o = new java.lang.RuntimeException(new String(
                ""+new java.lang.Integer(""+new java.lang.Long(""+new java.lang.Boolean()))),
                new java.lang.Throwable("",new IOException(""+new Object())));

        return 0;
    }
}

