// "Replace explicit type with 'var'" "true"
class Main {
    void m(String[] args) {
        for (<caret>String arg : args) ;
    }
}