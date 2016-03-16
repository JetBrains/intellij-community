import java.io.Serializable;
import java.math.BigInteger;

class C extends B<String, BigInteger> {
  @Override
  public <S extends String> S save(S entity) {
    return super.save(entity);
  }
}


class B<T, ID extends Serializable> implements A<T, ID> {
  public <S extends T> S save(S entity) {
    return null;
  }
}

interface A<T, ID extends Serializable>  {
  <S extends T> S save(S var1);
}
