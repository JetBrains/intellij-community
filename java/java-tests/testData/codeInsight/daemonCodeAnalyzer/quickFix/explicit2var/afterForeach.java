// "Replace explicit type with 'var'" "true"
class Main {
    void m(String[] args) {
        for (@Anno var arg : args) ;
    }
}
@interface Anno {}