// "Split values of 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            /*1*/<caret>case /*2*/1,/*3*/ 2/*4*/ ->/*5*/ s /*6*/= "x";/*7*/ //8
        }
    }
}