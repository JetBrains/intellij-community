interface Foo extends Runnable {
    @Override
    default void run() {
        <caret>
    }
}
