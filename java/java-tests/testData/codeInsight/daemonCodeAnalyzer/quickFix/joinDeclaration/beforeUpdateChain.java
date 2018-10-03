// "Join declaration and assignment" "INFORMATION"
class C {
    int foo (int a, int b){
        int x;
        <caret>x = a;
        x = x * 31 + b;
        return x;
    }
}