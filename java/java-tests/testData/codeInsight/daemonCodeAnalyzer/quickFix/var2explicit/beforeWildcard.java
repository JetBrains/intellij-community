// "Replace 'var' with explicit type" "true-preview"
class Main {
    void m(java.util.List<? extends String> args){
        <caret>var s = args.get(0);
    }
}