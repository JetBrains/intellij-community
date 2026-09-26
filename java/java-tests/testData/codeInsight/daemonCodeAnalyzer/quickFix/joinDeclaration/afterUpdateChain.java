// "Join declaration and assignment" "INFORMATION"
class C {
    int foo (int a, int b){
        int x = a;
        x = x * 31 + b;
        return x;
    }
}