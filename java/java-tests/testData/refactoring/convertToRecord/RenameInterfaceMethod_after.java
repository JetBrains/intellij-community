interface IR {
    int first();
}

record R(int first) implements IR {
    R(int first) {
        this.first = first;
    }

    @Override
    public int first() {
        return first > 0 ? first : -first;
    }
}

class R2 implements IR {
    @Override
    public int first() {
      return 0;
    }
}