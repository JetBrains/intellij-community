// "Remove variable 'i'" "true"
import java.io.*;

class a {
    int k;
    private void run() {
        int <caret>i = ((k=9));
        i+=k;
        i=(i=(k=9)==0 ? k=8 : 0);
        i = 9;
        while ((i=1) > 0) i=1;
        for (;;i++) i=0;
    }
}

