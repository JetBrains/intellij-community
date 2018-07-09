// "Replace 'var' with explicit type" "true"
class Main {
    void m(java.util.List<? extends String> args){
        String s = args.get(0);
    }
}