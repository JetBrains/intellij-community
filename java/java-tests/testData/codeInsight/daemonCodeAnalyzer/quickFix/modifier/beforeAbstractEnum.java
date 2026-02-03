// "Make 'X' abstract" "false"
enum X implements Runnable {
    A {
        @Override
        public void run() {

        }
    }, <caret>B;
}