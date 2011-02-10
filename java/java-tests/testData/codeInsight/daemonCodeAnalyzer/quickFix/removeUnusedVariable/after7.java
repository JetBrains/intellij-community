// "Remove variable 'i'" "true"

class a {
    int k;
    private void run() {
        <caret>while (1 > 0) ;
        for (;; ) ;
    }
}

