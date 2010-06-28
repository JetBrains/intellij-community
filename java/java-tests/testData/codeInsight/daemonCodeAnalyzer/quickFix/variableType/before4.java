// "Change 's' type to 'java.lang.Runnable'" "true"

class a {
    String s = <caret>new Runnable() {
        public void run() { }
    };
}

