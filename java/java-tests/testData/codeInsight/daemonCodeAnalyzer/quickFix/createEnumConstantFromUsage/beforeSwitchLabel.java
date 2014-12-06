// "Create enum constant 'D3'" "true"
public enum Demo {
}

class Show {

    public void doSomething(Demo d) {
        switch (d) {
          case D<caret>3:
            break;
        }
    }
}