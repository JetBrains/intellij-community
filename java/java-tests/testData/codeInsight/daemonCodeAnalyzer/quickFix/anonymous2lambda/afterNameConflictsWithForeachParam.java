// "Replace with lambda" "true"
class Test2 {

    void foo(final List<PatchLogger> loggers) {
        final PatchLogger logger = s -> {
            for (PatchLogger logger1 : loggers) {
                logger1.logOperation(s);
            }
        };
    }

    public interface PatchLogger {
        void logOperation(String logger);
    }
}
