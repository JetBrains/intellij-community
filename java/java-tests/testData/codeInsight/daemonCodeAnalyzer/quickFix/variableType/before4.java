// "Change field 's' type to 'Runnable'" "true-preview"

class a {
    String s = <caret>new Runnable() {
        public void run() { }
    };
}

