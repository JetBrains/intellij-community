// "Change field 's' type to 'Runnable'" "true"

class a {
    String s = <caret>new Runnable() {
        public void run() { }
    };
}

