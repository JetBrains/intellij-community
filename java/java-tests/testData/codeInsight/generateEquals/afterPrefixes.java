class Simple {
  private int simple = 1;

    @Override
    public boolean equals(Object p_o_r) {
        if (p_o_r == null || getClass() != p_o_r.getClass()) return false;

        final Simple l_that_v = (Simple) p_o_r;
        return simple == l_that_v.simple;
    }

    @Override
    public int hashCode() {
        return simple;
    }
}