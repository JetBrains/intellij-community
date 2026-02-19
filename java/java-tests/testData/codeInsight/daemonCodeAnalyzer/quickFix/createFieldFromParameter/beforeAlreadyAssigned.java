// "Create field for parameter 'p1'" "true-preview"

class Test{
    int b;
    Test(String p<caret>1){
        b = p1.length();
    }
}

