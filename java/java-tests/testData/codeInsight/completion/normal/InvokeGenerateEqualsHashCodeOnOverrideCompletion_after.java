import java.util.Objects;

class A {
  int a;

    @Override
    public boolean equals(Object o) {<caret>
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        A a1 = (A) o;
        return a == a1.a;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a);
    }
}
