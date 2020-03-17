import java.io.*;

class Main {
    void test() {
        new <caret>Logger(System.out).log("foo");
    }
}

class Logger {
    private final PrintStream ps;
    
    Logger(PrintStream ps) {
        this.ps = ps;
    }
    
    void log(Object obj) {
        ps.println(obj);
    }
}