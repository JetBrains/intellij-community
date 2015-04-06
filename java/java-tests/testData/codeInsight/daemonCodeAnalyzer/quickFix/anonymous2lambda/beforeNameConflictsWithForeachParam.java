// "Replace with lambda" "true"
class Test2 {

    void foo(final List<PatchLogger> loggers) {
        final PatchLogger logger = new Patch<caret>Logger() {
            @Override
            public void logOperation(String s) {
                for (PatchLogger logger : loggers) {
                    logger.logOperation(s);
                }
            }
        };
    }

    public interface PatchLogger {
        void logOperation(String logger);
    }
}
