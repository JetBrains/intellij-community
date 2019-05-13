// "Bring 'int j' into scope" "true"
class a {
    void foo() {
        int j;
        {
            int i;
            j = 10;
        }
        System.out.println(<caret>j); // invoke "bring 'int j' into scope" quickfix here
    }
}