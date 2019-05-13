// "Create enum constant 'D3'" "true"
public enum Demo {
    D3
}

class Show {

    public void doSomething(Demo d) {
        switch (d) {
          case D3:
            break;
        }
    }
}