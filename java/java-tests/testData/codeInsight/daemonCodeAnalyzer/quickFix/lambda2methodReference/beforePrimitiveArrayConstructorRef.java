// "Replace lambda with method reference" "true"
class Example {
    interface Jjj {
        int[] jjj(int p);
    }

    {
        Jjj jjj = (p) -> new i<caret>nt[p];
    }
}