// "Extract interface" "false"
public class Test{
    void main(){
        new Int(){
            @Overr<caret>ide
            void foo(){

            }
        };
    }
}

class Int {}