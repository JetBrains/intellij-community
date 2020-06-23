class Test {
    void method(){         
        <selection>try {
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
        }</selection>
        someOtherCode();
    }
}