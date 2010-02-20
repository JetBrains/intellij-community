public class ExpData {
  public void use(Object p) {
    System.out.println(p);
  }

  public void context() {
    ObjectType v1 = new ObjectType();
    v1 = v1.provide();
    inline1(v1);

    int v2 = 1;
    v2 += System.identityHashCode(new Object());
    inline2(v2);
  }

  public void inline1(ObjectType <caret>subj) {
    use(subj);
  }

  public void inline2(int subj) {
    use(subj);
  }
}

class ObjectType {
  private int value = 1;

  public ObjectType provide() {
    return new ObjectType();
  }
}
