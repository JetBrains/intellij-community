// "Create property" "true"
class Calculator {
    private Object i;

    {
      setI(() -> {}); 
    }

    public void setI(Object i) {
        this.i = i;
    }

    public Object getI() {
        return i;
    }
}
