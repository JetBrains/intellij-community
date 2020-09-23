interface IR {
    int getFirst();
}

class <caret>R implements IR {
    final int first;

    R(int first) {
      this.first = first;
    }

    @Override
    public int getFirst() {
      return first > 0 ? first : -first;
    }
}

class R2 implements IR {
    @Override
    public int getFirst() {
      return 0;
    }
}