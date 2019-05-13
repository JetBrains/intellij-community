// "Replace explicit type with 'var'" "true"
class Main {
    void m(String[] args) {
        for (@Anno <caret>String arg : args) ;
    }
}
@interface Anno {}