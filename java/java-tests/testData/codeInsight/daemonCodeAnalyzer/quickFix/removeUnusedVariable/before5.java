// "Remove field 'i'" "true"
import java.io.*;

class a {
    private int <caret>i = 0;
    private void run() {
        int j;
        i++;
        int k = 9;
        i=(i=(k=9)==0 ? k=8 : 0);
        i = 9;
        if ((i=3)==0) i=0;
    }
}

