// "Convert to a record" "true"
interface IR1 {
    int getFirst();
}

interface IR2 {
    int getFirst();
}

class <caret>R implements IR1, IR2 {
    final int first;

    R(int first) {
      this.first = first;
    }

    @Override
    public int getFirst() {
      return first > 0 ? first : -first;
    }
}

class R2 implements IR1 {
    @Override
    public int getFirst() {
      return 0;
    }
}