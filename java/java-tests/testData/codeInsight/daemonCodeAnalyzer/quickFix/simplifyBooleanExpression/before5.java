// "Remove 'if' statement" "true-preview"
class X {
    void f() {
        int i = 2;
        if (false<caret> !=false) {
            //sdf
        }
        else {
            // begin
            int o = 0; //mid
            // end
        }
    }
}