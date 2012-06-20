public class Demo {
    void foo(Integer i){
        switch(i) {
            case Types.CH<caret>
        }
    }
}

interface Types {
  int CHAR = 2;
}