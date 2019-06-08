// "Replace loop with 'Arrays.setAll()' method call" "true"
public class Test {
    private Object[] data;

    public void fill() {
        for(int <caret>idx = 0; (this./*comment*/data).length > idx; idx+=1) {
            /*in body*/
            this./*in lvalue*/data[idx] = "Hello!" + /* we need index here */ idx;
        }
    }
}