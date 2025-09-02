// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
interface IR1 {
    int first();
}

interface IR2 {
    int first();
}

record R(int first) implements IR1, IR2 {

    @Override
    public int first() {
        return first > 0 ? first : -first;
    }
}

class R2 implements IR1 {
    @Override
    public int first() {
      return 0;
    }
}
