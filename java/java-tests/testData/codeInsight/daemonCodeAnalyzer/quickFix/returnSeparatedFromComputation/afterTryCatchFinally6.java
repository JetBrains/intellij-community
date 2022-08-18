// "Move 'return' closer to computation of the value of 'n'" "true-preview"
import java.io.*;

class T {
    int f(boolean b, boolean c, boolean d) {
        int n = -1;
        try {
            n = 1;
            if (b) throw new IOException();
            n = 2;
            if (c) throw new RuntimeException();
            return n;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        catch (RuntimeException e) {
            return 3;
        }
        finally {
            if(d) return 4;
        }
    }
}