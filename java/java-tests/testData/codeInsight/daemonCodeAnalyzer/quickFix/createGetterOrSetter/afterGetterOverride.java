// "Create getter for 's'" "true-preview"
public class A extends Parent{
    private String s;

    @Override
    public String getS() {
        return s;
    }
}

class Parent {
  public String getS() {}
}