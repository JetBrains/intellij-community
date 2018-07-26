// "Replace with lambda" "true"
class Test2 {

    void foo(final List<PatchLogger> loggers) {
        final PatchLogger logger = logger1 -> {
            for (PatchLogger logger2 : loggers) {
                logger2.logOperation(logger1);
            }
        };
    }

    public interface PatchLogger {
        void logOperation(String logger);
    }
}
