// "Remove variable 'i'" "true"
import java.io.*;

class a {
    private int run() {
        <caret>int j;
        int k = 9;
        if (3 ==0) ;
        else return k;
        return 0;
    }
}

