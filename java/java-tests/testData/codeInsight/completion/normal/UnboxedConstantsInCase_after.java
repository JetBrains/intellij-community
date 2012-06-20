public class Demo {
    void foo(Integer i){
        switch(i) {
            case Types.CHAR<caret>
        }
    }
}

interface Types {
  int CHAR = 2;
}