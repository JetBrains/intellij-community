class Test {
    void method(){
        newMethod();
        someOtherCode();
    }

    private void newMethod() {
        try {
            process.waitFor();
        }
        catch(InterruptedException e) {
            process.destroy();
        }
        finally {
            try {
                myParsingThread.join();
            }
            catch(InterruptedException e) {
            }
            compilerHandler.processTerminated();
        }
        synchronized (this) {
            myParsingThread = null;
        }
    }
}