public class Aaaaaaa {

    void bar(Object foo) {
        if (foo instanceof String) {
          ((String) foo).substr<caret>
        }
    }

}
