// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            /*1*/case /*2*/1/*3*//*4*/ ->/*5*/ s /*6*/= "x";
            case 2 -> s /*6*/= "x";/*7*/ //8
        }
    }
}